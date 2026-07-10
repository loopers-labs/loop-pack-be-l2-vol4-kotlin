package com.loopers.payment.application

import com.loopers.domain.BaseEntity
import com.loopers.order.application.OrderFacade
import com.loopers.order.domain.Order
import com.loopers.order.domain.OrderItemSnapshot
import com.loopers.order.domain.OrderRepository
import com.loopers.order.domain.OrderStatus
import com.loopers.payment.domain.CardType
import com.loopers.payment.domain.Payment
import com.loopers.payment.domain.PaymentRepository
import com.loopers.payment.domain.PaymentStatus
import com.loopers.shared.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.ZonedDateTime

class PaymentServiceTest {
    private val paymentRepository: PaymentRepository = mock()
    private val orderRepository: OrderRepository = mock()
    private val orderFacade: OrderFacade = mock()
    private val alertSender: AlertSender = mock()
    private val service = PaymentService(paymentRepository, orderRepository, orderFacade, alertSender)

    private fun <T : BaseEntity> T.withId(id: Long): T = apply {
        val field = BaseEntity::class.java.getDeclaredField("id")
        field.isAccessible = true
        field.set(this, id)
    }

    private fun Payment.withCreatedAt(at: ZonedDateTime): Payment = apply {
        val field = BaseEntity::class.java.getDeclaredField("createdAt")
        field.isAccessible = true
        field.set(this, at)
    }

    private fun payment() = Payment.create(orderId = 10L, userId = 1L, amount = Money(1_000), cardType = CardType.SAMSUNG)

    private fun order(): Order = Order.create(
        userId = 1L,
        snapshots = listOf(
            OrderItemSnapshot(
                productId = 100L,
                brandId = null,
                productName = "상품",
                brandName = null,
                unitPrice = Money(1_000),
                quantity = 1,
            ),
        ),
        couponId = 5L,
    ).withId(10L)

    private fun callbackCommand(status: PaymentResultStatus, transactionKey: String) =
        PaymentCallbackCommand(orderKey = "order-key", transactionKey = transactionKey, status = status, reason = null)

    @DisplayName("reflectSubmit(Accepted): transactionKey 만 저장하고 보상하지 않는다.")
    @Test
    fun reflectSubmit_accepted() {
        val payment = payment()
        whenever(paymentRepository.findById(1L)).thenReturn(payment)

        val info = service.reflectSubmit(1L, PgSubmitResult.Accepted("tx-1"))

        assertAll(
            { assertThat(payment.transactionKey).isEqualTo("tx-1") },
            { assertThat(payment.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(info.status).isEqualTo(PaymentStatus.PENDING) },
            { verify(orderFacade, never()).cancelAndCompensate(any()) },
        )
    }

    @DisplayName("reflectSubmit(Failed): 결제 실패 처리하고 주문 취소·보상을 호출한다.")
    @Test
    fun reflectSubmit_failed_compensates() {
        val payment = payment()
        val order = order()
        whenever(paymentRepository.findById(1L)).thenReturn(payment)
        whenever(orderRepository.findById(10L)).thenReturn(order)

        service.reflectSubmit(1L, PgSubmitResult.Failed)

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
            { verify(orderFacade).cancelAndCompensate(order) },
        )
    }

    @DisplayName("reflectSubmit(Rejected): 사유를 남겨 실패 처리하고 보상한다.")
    @Test
    fun reflectSubmit_rejected_compensates() {
        val payment = payment()
        val order = order()
        whenever(paymentRepository.findById(1L)).thenReturn(payment)
        whenever(orderRepository.findById(10L)).thenReturn(order)

        service.reflectSubmit(1L, PgSubmitResult.Rejected("한도 초과"))

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
            { assertThat(payment.reason).isEqualTo("한도 초과") },
            { verify(orderFacade).cancelAndCompensate(order) },
        )
    }

    @DisplayName("reflectSubmit(Unknown): 결제·주문을 UNKNOWN 으로 두고 보상하지 않는다.")
    @Test
    fun reflectSubmit_unknown_noCompensation() {
        val payment = payment()
        val order = order()
        whenever(paymentRepository.findById(1L)).thenReturn(payment)
        whenever(orderRepository.findById(10L)).thenReturn(order)

        service.reflectSubmit(1L, PgSubmitResult.Unknown)

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.UNKNOWN) },
            { assertThat(order.status).isEqualTo(OrderStatus.UNKNOWN) },
            { verify(orderFacade, never()).cancelAndCompensate(any()) },
        )
    }

    @DisplayName("handleCallback(SUCCESS): orderKey 로 찾아 성공 확정하고 비어있던 transactionKey 를 채운다.")
    @Test
    fun handleCallback_success() {
        val payment = payment()
        val order = order()
        whenever(orderRepository.findByOrderKey("order-key")).thenReturn(order)
        whenever(paymentRepository.findByOrderId(10L)).thenReturn(payment)
        whenever(orderRepository.findById(10L)).thenReturn(order)

        service.handleCallback(callbackCommand(PaymentResultStatus.SUCCESS, "tx-9"))

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS) },
            { assertThat(payment.transactionKey).isEqualTo("tx-9") },
            { assertThat(order.status).isEqualTo(OrderStatus.PAID) },
        )
    }

    @DisplayName("handleCallback(FAILED): 실패 확정하고 주문 취소·보상을 호출한다.")
    @Test
    fun handleCallback_failed_compensates() {
        val payment = payment()
        val order = order()
        whenever(orderRepository.findByOrderKey("order-key")).thenReturn(order)
        whenever(paymentRepository.findByOrderId(10L)).thenReturn(payment)
        whenever(orderRepository.findById(10L)).thenReturn(order)

        service.handleCallback(callbackCommand(PaymentResultStatus.FAILED, "tx-9"))

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
            { verify(orderFacade).cancelAndCompensate(order) },
        )
    }

    @DisplayName("handleCallback: 이미 확정(SUCCESS)된 건이면 멱등하게 아무 것도 하지 않는다.")
    @Test
    fun handleCallback_idempotent() {
        val payment = payment().apply { success() }
        val order = order()
        whenever(orderRepository.findByOrderKey("order-key")).thenReturn(order)
        whenever(paymentRepository.findByOrderId(10L)).thenReturn(payment)

        service.handleCallback(callbackCommand(PaymentResultStatus.SUCCESS, "tx-9"))

        assertAll(
            { assertThat(order.status).isEqualTo(OrderStatus.PENDING_PAYMENT) },
            { verify(orderFacade, never()).cancelAndCompensate(any()) },
            { verifyNoInteractions(alertSender) },
        )
    }

    @DisplayName("handleCallback: 실패 처리된 건에 성공 통보가 오면 충돌 알람을 보낸다.")
    @Test
    fun handleCallback_conflict_alerts() {
        val payment = payment().apply { fail("미전송 확정") }
        val order = order()
        whenever(orderRepository.findByOrderKey("order-key")).thenReturn(order)
        whenever(paymentRepository.findByOrderId(10L)).thenReturn(payment)

        service.handleCallback(callbackCommand(PaymentResultStatus.SUCCESS, "tx-9"))

        assertAll(
            { verify(alertSender).alert(any()) },
            { verify(orderFacade, never()).cancelAndCompensate(any()) },
        )
    }

    @DisplayName("applyReconcile(Found SUCCESS): 성공 확정하고 RESOLVED 를 반환한다.")
    @Test
    fun applyReconcile_foundSuccess() {
        val payment = payment().apply { markUnknown() }.withCreatedAt(ZonedDateTime.now())
        val order = order().apply { markUnknown() }
        whenever(paymentRepository.findById(1L)).thenReturn(payment)
        whenever(orderRepository.findById(10L)).thenReturn(order)

        val outcome = service.applyReconcile(1L, PgQueryResult.Found("tx-9", PaymentResultStatus.SUCCESS))

        assertAll(
            { assertThat(outcome).isEqualTo(ReconcileOutcome.RESOLVED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS) },
            { assertThat(order.status).isEqualTo(OrderStatus.PAID) },
        )
    }

    @DisplayName("applyReconcile(Found FAILED): 실패 확정하고 보상한다.")
    @Test
    fun applyReconcile_foundFailed() {
        val payment = payment().apply { markUnknown() }.withCreatedAt(ZonedDateTime.now())
        val order = order().apply { markUnknown() }
        whenever(paymentRepository.findById(1L)).thenReturn(payment)
        whenever(orderRepository.findById(10L)).thenReturn(order)

        val outcome = service.applyReconcile(1L, PgQueryResult.Found("tx-9", PaymentResultStatus.FAILED))

        assertAll(
            { assertThat(outcome).isEqualTo(ReconcileOutcome.RESOLVED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
            { verify(orderFacade).cancelAndCompensate(order) },
        )
    }

    @DisplayName("applyReconcile(NotFound, deadline 이내): 아직 두고 PENDING 을 반환한다.")
    @Test
    fun applyReconcile_notFound_withinDeadline() {
        val payment = payment().apply { markUnknown() }.withCreatedAt(ZonedDateTime.now())
        whenever(paymentRepository.findById(1L)).thenReturn(payment)

        val outcome = service.applyReconcile(1L, PgQueryResult.NotFound)

        assertAll(
            { assertThat(outcome).isEqualTo(ReconcileOutcome.PENDING) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.UNKNOWN) },
            { verify(orderFacade, never()).cancelAndCompensate(any()) },
        )
    }

    @DisplayName("applyReconcile(NotFound, deadline 초과): 미전송 확정으로 실패 처리하고 보상한다.")
    @Test
    fun applyReconcile_notFound_overDeadline() {
        val payment = payment().apply { markUnknown() }.withCreatedAt(ZonedDateTime.now().minusHours(1))
        val order = order().apply { markUnknown() }
        whenever(paymentRepository.findById(1L)).thenReturn(payment)
        whenever(orderRepository.findById(10L)).thenReturn(order)

        val outcome = service.applyReconcile(1L, PgQueryResult.NotFound)

        assertAll(
            { assertThat(outcome).isEqualTo(ReconcileOutcome.RESOLVED) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
            { verify(orderFacade).cancelAndCompensate(order) },
        )
    }

    @DisplayName("applyReconcile(Unreachable, deadline 초과): 함부로 실패시키지 않고 알람 대상으로 둔다.")
    @Test
    fun applyReconcile_unreachable_overDeadline() {
        val payment = payment().apply { markUnknown() }.withCreatedAt(ZonedDateTime.now().minusHours(1))
        whenever(paymentRepository.findById(1L)).thenReturn(payment)

        val outcome = service.applyReconcile(1L, PgQueryResult.Unreachable)

        assertAll(
            { assertThat(outcome).isEqualTo(ReconcileOutcome.NEEDS_ALERT) },
            { assertThat(payment.status).isEqualTo(PaymentStatus.UNKNOWN) },
            { verify(orderFacade, never()).cancelAndCompensate(any()) },
        )
    }

    @DisplayName("applyReconcile: 이미 확정된 건이면 RESOLVED 로 즉시 반환한다.")
    @Test
    fun applyReconcile_alreadyTerminal() {
        val payment = payment().apply { success() }
        whenever(paymentRepository.findById(1L)).thenReturn(payment)

        val outcome = service.applyReconcile(1L, PgQueryResult.NotFound)

        assertAll(
            { assertThat(outcome).isEqualTo(ReconcileOutcome.RESOLVED) },
            { verify(orderFacade, never()).cancelAndCompensate(any()) },
        )
    }
}
