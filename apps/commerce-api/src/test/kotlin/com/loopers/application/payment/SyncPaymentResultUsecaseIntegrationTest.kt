package com.loopers.application.payment

import com.loopers.application.payment.usecase.SyncPaymentResultUsecase
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgStatus
import com.loopers.domain.product.ProductStockRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
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

        syncPaymentResultUsecase.apply(SyncPaymentResultCommand("tx-1", ctx.orderId, PgStatus.SUCCESS, null))

        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.PAID)
        assertThat(paymentRepository.findByOrderId(ctx.orderId)?.status).isEqualTo(PaymentStatus.SUCCESS)
    }

    @Test
    fun `실패 콜백이면 주문 FAILED 와 함께 재고가 복구된다`() {
        val ctx = fixtures.pendingOrder() // 주문 생성으로 재고가 이미 차감된 상태
        val stockBefore = productStockRepository.findByProductId(ctx.productId)!!.quantity
        savePendingPayment(ctx.orderId, ctx.userId, ctx.paidPrice, "tx-2")

        syncPaymentResultUsecase.apply(
            SyncPaymentResultCommand("tx-2", ctx.orderId, PgStatus.FAILED, PaymentFailureReason.LIMIT_EXCEEDED),
        )

        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.FAILED)
        assertThat(productStockRepository.findByProductId(ctx.productId)!!.quantity)
            .isEqualTo(stockBefore + ctx.quantity)
    }

    @Test
    fun `같은 콜백을 두 번 받아도 결과는 동일하다(멱등)`() {
        val ctx = fixtures.pendingOrder()
        savePendingPayment(ctx.orderId, ctx.userId, ctx.paidPrice, "tx-3")

        syncPaymentResultUsecase.apply(SyncPaymentResultCommand("tx-3", ctx.orderId, PgStatus.SUCCESS, null))
        syncPaymentResultUsecase.apply(SyncPaymentResultCommand("tx-3", ctx.orderId, PgStatus.SUCCESS, null)) // 재수신

        assertThat(orderRepository.findById(ctx.orderId)?.status).isEqualTo(OrderStatus.PAID)
    }
}
