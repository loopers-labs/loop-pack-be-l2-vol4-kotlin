package com.loopers.application.order

import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.order.OrderCancelReason
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.StockReservationStatus
import com.loopers.domain.payment.PaymentStatus
import com.loopers.infrastructure.catalog.ProductStockJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.order.StockReservationJpaRepository
import com.loopers.infrastructure.payment.FakePaymentGateway
import com.loopers.infrastructure.payment.PaymentJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.LocalDateTime

@SpringBootTest
class OrderCheckoutFacadeIntegrationTest @Autowired constructor(
    private val facade: OrderCheckoutFacade,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val stockReservationJpaRepository: StockReservationJpaRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val paymentGateway: FakePaymentGateway,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        paymentGateway.reset()
        databaseCleanUp.truncateAllTables()
    }

    private fun checkoutCommand(expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10)) =
        OrderCommand.Checkout(
            userId = 1L,
            items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
            deliveryAddress = "서울시 강남구",
            deliveryRequest = "문 앞",
            phoneNumber = "010-1234-5678",
            reservationExpiresAt = expiresAt,
        )

    @Test
    fun checkoutRollbackLeavesNoOrderOrReservationWhenReservationFails() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 1))

        val ex = assertThrows<CoreException> {
            facade.checkout(checkoutCommand())
        }

        assertAll(
            { assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT) },
            { assertThat(orderJpaRepository.count()).isZero() },
            { assertThat(stockReservationJpaRepository.count()).isZero() },
            { assertThat(paymentJpaRepository.count()).isZero() },
        )
    }

    @Test
    fun checkoutCreatesPaymentReadyAndInProgressReservation() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))

        val checkout = facade.checkout(checkoutCommand())

        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(checkout.status).isEqualTo(OrderStatus.PAYMENT_PENDING) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.READY) },
            { assertThat(payment.requestedAmount).isEqualTo(2000L) },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.IN_PROGRESS) },
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isEqualTo(2) },
        )
    }

    @Test
    fun paymentSuccessRunsPgApproveOutsideTransactionAndCompletesOrder() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = facade.checkout(checkoutCommand())

        val paid = facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        assertAll(
            { assertThat(paid.status).isEqualTo(OrderStatus.COMPLETED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED) },
            { assertThat(payment.paymentKey).isEqualTo("payment-key-${checkout.orderId}") },
            { assertThat(payment.pgTransactionId).isEqualTo("payment-${checkout.orderId}") },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.COMPLETED) },
            { assertThat(stock.stockQuantity).isEqualTo(3) },
            { assertThat(stock.reservedQuantity).isZero() },
            { assertThat(paymentGateway.transactionActiveDuringApprove).containsExactly(false) },
        )
    }

    @Test
    fun approvedPaymentWithInternalCompletionFailureLeavesOrderFailedPaymentCompletionFailedAndReservationInProgress() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = facade.checkout(checkoutCommand())
        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        stock.reservedQuantity = 0
        productStockJpaRepository.saveAndFlush(stock)

        val ex = assertThrows<CoreException> {
            facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))
        }

        val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
        assertAll(
            { assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT) },
            { assertThat(order.status).isEqualTo(OrderStatus.FAILED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETION_FAILED) },
            { assertThat(payment.completionRetryCount).isZero() },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.IN_PROGRESS) },
        )
    }

    @Test
    fun paymentFailureKeepsOrderPaymentPendingAndReservationActive() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = facade.checkout(checkoutCommand())
        paymentGateway.failNextApproval()

        val ex = assertThrows<CoreException> {
            facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))
        }

        val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
        val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
        assertAll(
            { assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
            { assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_PENDING) },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.IN_PROGRESS) },
        )
    }

    @Test
    fun cancelBeforePaymentCancelsOrderPaymentReservationAndReleasesReservedQuantityWithoutPgCancel() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = facade.checkout(checkoutCommand())

        val canceled = facade.cancel(OrderCommand.Cancel(checkout.orderId))

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
        assertAll(
            { assertThat(canceled.status).isEqualTo(OrderStatus.CANCELED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED) },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED) },
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isZero() },
            { assertThat(paymentGateway.canceledTransactionIds).isEmpty() },
        )
    }

    @Test
    fun cancelAfterPaymentRunsPgCancelOutsideTransactionRestoresStockAndCancelsReservation() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = facade.checkout(checkoutCommand())
        facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))

        val canceled = facade.cancel(OrderCommand.Cancel(checkout.orderId))

        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
        assertAll(
            { assertThat(canceled.status).isEqualTo(OrderStatus.CANCELED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.CANCELED) },
            { assertThat(stock.stockQuantity).isEqualTo(5) },
            { assertThat(stock.reservedQuantity).isZero() },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.CANCELED) },
            { assertThat(paymentGateway.canceledTransactionIds).containsExactly("payment-${checkout.orderId}") },
            { assertThat(paymentGateway.transactionActiveDuringCancel).containsExactly(false) },
        )
    }

    @Test
    fun pgCancelSuccessWithRestoreFailureLeavesOrderFailedAndPaymentCompletionFailed() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = facade.checkout(checkoutCommand())
        facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))
        stockReservationJpaRepository.deleteAll(stockReservationJpaRepository.findAllByOrderId(checkout.orderId))
        stockReservationJpaRepository.flush()

        val ex = assertThrows<CoreException> {
            facade.cancel(OrderCommand.Cancel(checkout.orderId))
        }

        val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        assertAll(
            { assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT) },
            { assertThat(order.status).isEqualTo(OrderStatus.FAILED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETION_FAILED) },
        )
    }

    @Test
    fun failedCompletionCanRetryAfterReservationExpiresWhenPgVerifySucceeds() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = facade.checkout(checkoutCommand())
        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        stock.reservedQuantity = 0
        productStockJpaRepository.saveAndFlush(stock)
        assertThrows<CoreException> {
            facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))
        }
        stock.reservedQuantity = 2
        productStockJpaRepository.saveAndFlush(stock)
        expireReservation(checkout.orderId)

        val retried = facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "ignored-on-retry"))

        val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
        assertAll(
            { assertThat(retried.status).isEqualTo(OrderStatus.COMPLETED) },
            { assertThat(order.status).isEqualTo(OrderStatus.COMPLETED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED) },
            { assertThat(payment.completionRetryCount).isZero() },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.COMPLETED) },
            { assertThat(paymentGateway.transactionActiveDuringVerify).containsExactly(false) },
        )
    }

    @Test
    fun failedCompletionRetryFailureIncrementsCountAndStopsAtThree() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = facade.checkout(checkoutCommand())
        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        stock.reservedQuantity = 0
        productStockJpaRepository.saveAndFlush(stock)
        assertThrows<CoreException> {
            facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))
        }

        repeat(3) {
            assertThrows<CoreException> {
                facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "ignored-on-retry"))
            }
        }
        val ex = assertThrows<CoreException> {
            facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "ignored-on-retry"))
        }

        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        assertAll(
            { assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETION_FAILED) },
            { assertThat(payment.completionRetryCount).isEqualTo(3) },
        )
    }

    private fun expireReservation(orderId: Long) {
        jdbcTemplate.update(
            "update orders set reservation_expires_at = ? where id = ?",
            Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),
            orderId,
        )
    }

    @Test
    fun shippingStartedOrderCannotBeCanceled() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = facade.checkout(checkoutCommand())
        facade.pay(OrderCommand.Pay(checkout.orderId, paymentKey = "payment-key-${checkout.orderId}"))
        facade.startShipping(OrderCommand.StartShipping(checkout.orderId))

        val ex = assertThrows<CoreException> {
            facade.cancel(OrderCommand.Cancel(checkout.orderId))
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
    }

    @Test
    fun expireReservationsExpiresPendingOrderReservationAndPaymentAndReleasesReservedQuantity() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = facade.checkout(checkoutCommand(LocalDateTime.of(2026, 5, 29, 12, 0)))

        facade.expireReservations(OrderCommand.Expire(LocalDateTime.of(2026, 5, 29, 12, 1)))

        val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(order.status).isEqualTo(OrderStatus.EXPIRED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.EXPIRED) },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.EXPIRED) },
            { assertThat(stock.reservedQuantity).isZero() },
        )
    }
}
