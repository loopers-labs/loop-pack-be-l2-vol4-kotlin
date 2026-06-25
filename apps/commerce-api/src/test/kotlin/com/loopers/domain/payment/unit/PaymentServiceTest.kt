package com.loopers.domain.payment.unit

import com.loopers.domain.payment.application.service.PaymentService
import com.loopers.domain.payment.exception.InvalidPaymentException
import com.loopers.domain.payment.model.OutboxEventType
import com.loopers.domain.payment.model.PaymentStatus
import com.loopers.domain.payment.support.FakeOutboxRepository
import com.loopers.domain.payment.support.FakePaymentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentServiceTest {
    @Test
    fun `결제_요청_기록을_생성한다`() {
        val paymentRepository = FakePaymentRepository()
        val outboxRepository = FakeOutboxRepository()
        val paymentService = PaymentService(paymentRepository, outboxRepository)

        val payment = paymentService.request(orderId = 1L)
        val assigned = paymentService.assignTransactionKey(orderId = 1L, transactionKey = "20260625:TR:test01")

        assertThat(payment.id).isPositive()
        assertThat(payment.orderId).isEqualTo(1L)
        assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED)
        assertThat(assigned.externalTransactionKey).isEqualTo("20260625:TR:test01")
        assertThat(paymentRepository.lockedOrderIds).containsExactly(1L)
    }

    @Test
    fun `상태불명_전이는_상태재조회_outbox를_생성한다`() {
        val paymentRepository = FakePaymentRepository()
        val outboxRepository = FakeOutboxRepository()
        val paymentService = PaymentService(paymentRepository, outboxRepository)
        paymentService.request(orderId = 1L)

        val payment = paymentService.markUnknown(orderId = 1L, reason = "timeout")

        assertThat(payment.status).isEqualTo(PaymentStatus.UNKNOWN)
        assertThat(outboxRepository.events).hasSize(1)
        assertThat(outboxRepository.events.first().type).isEqualTo(OutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED)
    }

    @Test
    fun `완료_결제의_중복_승인은_상태를_변경하지_않고_outbox를_추가하지_않는다`() {
        val paymentRepository = FakePaymentRepository()
        val outboxRepository = FakeOutboxRepository()
        val paymentService = PaymentService(paymentRepository, outboxRepository)
        paymentService.request(orderId = 1L)
        paymentService.assignTransactionKey(orderId = 1L, transactionKey = "20260625:TR:test01")
        val first = paymentService.approveByTransactionKey("20260625:TR:test01")

        val duplicated = paymentService.approveByTransactionKey("20260625:TR:test01")

        assertThat(first.changed).isTrue()
        assertThat(duplicated.changed).isFalse()
        assertThat(outboxRepository.events).hasSize(1)
        assertThat(outboxRepository.events.first().type).isEqualTo(OutboxEventType.PAYMENT_APPROVED)
        assertThat(paymentRepository.lockedOrderIds).containsExactly(1L)
        assertThat(paymentRepository.lockedTransactionKeys).containsExactly(
            "20260625:TR:test01",
            "20260625:TR:test01",
        )
    }

    @Test
    fun `외부_거래키는_다른_값으로_재할당할_수_없다`() {
        val paymentRepository = FakePaymentRepository()
        val outboxRepository = FakeOutboxRepository()
        val paymentService = PaymentService(paymentRepository, outboxRepository)
        paymentService.request(orderId = 1L)
        paymentService.assignTransactionKey(orderId = 1L, transactionKey = "20260625:TR:test01")

        assertThrows<InvalidPaymentException> {
            paymentService.assignTransactionKey(orderId = 1L, transactionKey = "20260625:TR:test02")
        }

        assertThat(paymentRepository.findByOrderIdOrNull(1L)?.externalTransactionKey)
            .isEqualTo("20260625:TR:test01")
        assertThat(paymentRepository.lockedOrderIds).containsExactly(1L, 1L)
    }

    @Test
    fun `승인_이후_실패_전이는_불가하다`() {
        val paymentRepository = FakePaymentRepository()
        val outboxRepository = FakeOutboxRepository()
        val paymentService = PaymentService(paymentRepository, outboxRepository)
        paymentService.request(orderId = 1L)
        paymentService.assignTransactionKey(orderId = 1L, transactionKey = "20260625:TR:test01")
        paymentService.approveByTransactionKey("20260625:TR:test01")

        assertThrows<InvalidPaymentException> {
            paymentService.failByTransactionKey("20260625:TR:test01", "late failed")
        }

        assertThat(paymentRepository.findByExternalTransactionKeyOrNull("20260625:TR:test01")?.status)
            .isEqualTo(PaymentStatus.APPROVED)
        assertThat(outboxRepository.events).hasSize(1)
    }

    @Test
    fun `실패_이후_승인_전이는_불가하다`() {
        val paymentRepository = FakePaymentRepository()
        val outboxRepository = FakeOutboxRepository()
        val paymentService = PaymentService(paymentRepository, outboxRepository)
        paymentService.request(orderId = 1L)
        paymentService.assignTransactionKey(orderId = 1L, transactionKey = "20260625:TR:test01")
        paymentService.failByTransactionKey("20260625:TR:test01", "한도초과")

        assertThrows<InvalidPaymentException> {
            paymentService.approveByTransactionKey("20260625:TR:test01")
        }

        assertThat(paymentRepository.findByExternalTransactionKeyOrNull("20260625:TR:test01")?.status)
            .isEqualTo(PaymentStatus.FAILED)
        assertThat(outboxRepository.events).hasSize(1)
    }
}
