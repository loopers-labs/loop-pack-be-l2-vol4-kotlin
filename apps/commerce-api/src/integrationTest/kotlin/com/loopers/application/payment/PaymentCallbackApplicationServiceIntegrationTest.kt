package com.loopers.application.payment

import com.loopers.application.order.OrderCheckoutFacade
import com.loopers.application.order.OrderInfo
import com.loopers.domain.catalog.ProductStock
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.StockReservationStatus
import com.loopers.domain.payment.PaymentStatus
import com.loopers.infrastructure.catalog.ProductStockJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.order.StockReservationJpaRepository
import com.loopers.infrastructure.payment.PaymentJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest
class PaymentCallbackApplicationServiceIntegrationTest @Autowired constructor(
    private val orderCheckoutFacade: OrderCheckoutFacade,
    private val callbackApplicationService: PaymentCallbackApplicationService,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val stockReservationJpaRepository: StockReservationJpaRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun successCallbackApprovesPaymentCompletesOrderAndDeductsReservedStock() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = checkoutAndRequestPayment()

        callbackApplicationService.handle(
            PaymentCallbackApplicationService.Command(
                transactionKey = "payment-${checkout.orderId}",
                orderId = checkout.orderId,
                amount = 2000L,
                status = "SUCCESS",
                reason = "정상 승인되었습니다.",
            ),
        )

        val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
        val stock = productStockJpaRepository.findByProductIdAndDeletedAtIsNull(10L)!!
        assertAll(
            { assertThat(order.status).isEqualTo(OrderStatus.COMPLETED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED) },
            { assertThat(payment.approvedAmount).isEqualTo(2000L) },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.COMPLETED) },
            { assertThat(stock.stockQuantity).isEqualTo(3) },
            { assertThat(stock.reservedQuantity).isZero() },
        )
    }

    @Test
    fun failedCallbackMarksPaymentVerifyFailedAndLeavesOrderPending() {
        productStockJpaRepository.save(ProductStock(productId = 10L, stockQuantity = 5))
        val checkout = checkoutAndRequestPayment()

        callbackApplicationService.handle(
            PaymentCallbackApplicationService.Command(
                transactionKey = "payment-${checkout.orderId}",
                orderId = checkout.orderId,
                amount = 2000L,
                status = "FAILED",
                reason = "잘못된 카드입니다. 다른 카드를 선택해주세요.",
            ),
        )

        val order = orderJpaRepository.findById(checkout.orderId).orElseThrow()
        val payment = paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(checkout.orderId)!!
        val reservation = stockReservationJpaRepository.findAllByOrderId(checkout.orderId).single()
        assertAll(
            { assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_PENDING) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.VERIFY_FAILED) },
            { assertThat(reservation.status).isEqualTo(StockReservationStatus.IN_PROGRESS) },
        )
    }

    private fun checkoutAndRequestPayment(): OrderInfo.Detail {
        val checkout = orderCheckoutFacade.checkout(
            OrderCommand.Checkout(
                userId = 1L,
                items = listOf(OrderCommand.CheckoutItem(10L, "상품A", "브랜드A", 1000L, 2)),
                deliveryAddress = "서울시 강남구",
                deliveryRequest = "문 앞",
                phoneNumber = "010-1234-5678",
                reservationExpiresAt = LocalDateTime.now().plusMinutes(10),
            ),
        )
        orderCheckoutFacade.pay(
            OrderCommand.Pay(
                userId = 1L,
                orderId = checkout.orderId,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-1234-5678",
            ),
        )
        return checkout
    }
}
