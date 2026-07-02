package com.loopers.application.payment

import com.loopers.application.payment.usecase.SyncPaymentResultUsecase
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgStatus
import com.loopers.domain.product.ProductStockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal

@SpringBootTest
class SyncPaymentResultUsecaseIntegrationTest @Autowired constructor(
    private val syncPaymentResultUsecase: SyncPaymentResultUsecase,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productStockRepository: ProductStockRepository,
    private val userCouponRepository: UserCouponRepository,
    private val fixtures: PaymentTestFixtures,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun savePendingPayment(orderId: Long, userId: Long, amount: BigDecimal, txKey: String): PaymentModel {
        val p = paymentRepository.save(
            PaymentModel(orderId, userId, amount, CardType.SAMSUNG, "1234-5678-9012-3456"),
        )
        p.assignTransactionKey(txKey)
        return paymentRepository.save(p)
    }

    @Test
    fun `성공 콜백이면 주문이 PAID 가 된다`() {
        val ctx = fixtures.pendingOrder()
        savePendingPayment(ctx.orderId, ctx.userId, ctx.paidPrice, "tx-1")

        syncPaymentResultUsecase.apply(
            SyncPaymentResultCommand("tx-1", ctx.orderId, amount = null, PgStatus.SUCCESS, null),
        )

        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.PAID)
        assertThat(paymentRepository.findByOrderId(ctx.orderId)?.status).isEqualTo(PaymentStatus.SUCCESS)
    }

    @Test
    fun `실패 콜백이면 주문 FAILED 와 함께 재고가 복구된다`() {
        val ctx = fixtures.pendingOrder() // 주문 생성으로 재고가 이미 차감된 상태
        val stockBefore = productStockRepository.findByProductId(ctx.productId)!!.quantity
        savePendingPayment(ctx.orderId, ctx.userId, ctx.paidPrice, "tx-2")

        syncPaymentResultUsecase.apply(
            SyncPaymentResultCommand("tx-2", ctx.orderId, amount = null, PgStatus.FAILED, PaymentFailureReason.LIMIT_EXCEEDED),
        )

        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.FAILED)
        assertThat(productStockRepository.findByProductId(ctx.productId)!!.quantity)
            .isEqualTo(stockBefore + ctx.quantity)
    }

    @Test
    fun `실패 콜백이면 쿠폰 적용 주문은 재고 복구와 함께 쿠폰이 원복된다`() {
        val ctx = fixtures.pendingOrderWithCoupon() // 쿠폰이 USED 로 적용된 PENDING 주문
        val stockBefore = productStockRepository.findByProductId(ctx.productId)!!.quantity
        savePendingPayment(ctx.orderId, ctx.userId, ctx.paidPrice, "tx-4")

        syncPaymentResultUsecase.apply(
            SyncPaymentResultCommand("tx-4", ctx.orderId, amount = null, PgStatus.FAILED, PaymentFailureReason.LIMIT_EXCEEDED),
        )

        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.FAILED)
        assertThat(productStockRepository.findByProductId(ctx.productId)!!.quantity)
            .isEqualTo(stockBefore + ctx.quantity)
        assertThat(userCouponRepository.findByIdAndUserId(ctx.userCouponId!!, ctx.userId)?.status)
            .isEqualTo(UserCouponStatus.AVAILABLE)
    }

    @Test
    fun `같은 콜백을 두 번 받아도 결과는 동일하다(CAS 멱등)`() {
        val ctx = fixtures.pendingOrder()
        savePendingPayment(ctx.orderId, ctx.userId, ctx.paidPrice, "tx-3")

        // 첫 번째 apply — CAS affected=1, 주문 PAID
        syncPaymentResultUsecase.apply(
            SyncPaymentResultCommand("tx-3", ctx.orderId, amount = null, PgStatus.SUCCESS, null),
        )
        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.PAID)

        // 두 번째 apply — CAS affected=0, no-op, 예외 없음, 주문 PAID 유지
        syncPaymentResultUsecase.apply(
            SyncPaymentResultCommand("tx-3", ctx.orderId, amount = null, PgStatus.SUCCESS, null),
        )

        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.PAID)
        assertThat(paymentRepository.findByOrderId(ctx.orderId)?.status).isEqualTo(PaymentStatus.SUCCESS)
    }

    @Test
    fun `콜백의 orderId가 결제의 orderId와 다르면 BAD_REQUEST를 반환한다`() {
        val ctx = fixtures.pendingOrder()
        savePendingPayment(ctx.orderId, ctx.userId, ctx.paidPrice, "tx-x")

        assertThatThrownBy {
            syncPaymentResultUsecase.apply(
                SyncPaymentResultCommand("tx-x", ctx.orderId + 9999, amount = null, PgStatus.SUCCESS, null),
            )
        }.isInstanceOf(CoreException::class.java)
            .satisfies({ assertThat((it as CoreException).errorType).isEqualTo(ErrorType.BAD_REQUEST) })

        // 주문 상태는 여전히 PENDING
        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.PENDING)
    }

    @Test
    fun `콜백 금액이 결제 금액과 다르면 BAD_REQUEST 이고 주문은 PENDING 유지된다`() {
        val ctx = fixtures.pendingOrder()
        savePendingPayment(ctx.orderId, ctx.userId, ctx.paidPrice, "tx-amt")

        val wrongAmount = ctx.paidPrice.toLong() + 1L

        assertThatThrownBy {
            syncPaymentResultUsecase.apply(
                SyncPaymentResultCommand("tx-amt", ctx.orderId, amount = wrongAmount, PgStatus.SUCCESS, null),
            )
        }.isInstanceOf(CoreException::class.java)
            .satisfies({ assertThat((it as CoreException).errorType).isEqualTo(ErrorType.BAD_REQUEST) })

        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.PENDING)
        assertThat(paymentRepository.findByOrderId(ctx.orderId)?.status).isEqualTo(PaymentStatus.PENDING)
    }

    @Test
    fun `CANCELLED 주문에 성공 콜백이 오면 payment 는 REFUND_REQUIRED 이고 주문은 CANCELLED 유지된다`() {
        val ctx = fixtures.cancelledOrder()
        savePendingPayment(ctx.orderId, ctx.userId, ctx.paidPrice, "tx-refund")

        syncPaymentResultUsecase.apply(
            SyncPaymentResultCommand("tx-refund", ctx.orderId, amount = null, PgStatus.SUCCESS, null),
        )

        assertThat(paymentRepository.findByOrderId(ctx.orderId)?.status).isEqualTo(PaymentStatus.REFUND_REQUIRED)
        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.CANCELLED)
    }

    @Test
    fun `I-2 CANCELLED 주문에 실패 콜백이 오면 payment 는 FAILED 이고 주문은 CANCELLED 유지되며 예외가 발생하지 않는다`() {
        val ctx = fixtures.cancelledOrder()
        val stockBefore = productStockRepository.findByProductId(ctx.productId)!!.quantity
        savePendingPayment(ctx.orderId, ctx.userId, ctx.paidPrice, "tx-fail-cancelled")

        // Should NOT throw — currently throws BAD_REQUEST from markAsFailed()
        syncPaymentResultUsecase.apply(
            SyncPaymentResultCommand("tx-fail-cancelled", ctx.orderId, amount = null, PgStatus.FAILED, PaymentFailureReason.LIMIT_EXCEEDED),
        )

        assertThat(paymentRepository.findByOrderId(ctx.orderId)?.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.CANCELLED)
        // Stock must NOT be double-restored
        assertThat(productStockRepository.findByProductId(ctx.productId)!!.quantity).isEqualTo(stockBefore)
    }
}
