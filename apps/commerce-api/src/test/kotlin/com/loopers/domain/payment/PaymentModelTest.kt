package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaymentModelTest {
    private fun newPayment() = PaymentModel(
        orderId = 1L,
        userId = 10L,
        amount = BigDecimal(1000),
        cardType = CardType.SAMSUNG,
        cardNo = "1234-5678-9012-3456",
    )

    @Test
    fun `생성 시 카드번호는 마스킹되어 마지막 4자리만 보존된다`() {
        val payment = newPayment()
        assertThat(payment.maskedCardNo).endsWith("3456")
        assertThat(payment.maskedCardNo).doesNotContain("1234-5678-9012")
        assertThat(payment.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(payment.isPending()).isTrue()
        assertThat(payment.transactionKey).isNull()
    }

    @Test
    fun `transactionKey 를 기록할 수 있다`() {
        val payment = newPayment()
        payment.assignTransactionKey("tx-123")
        assertThat(payment.transactionKey).isEqualTo("tx-123")
    }

    @Test
    fun `PENDING 결제는 성공으로 전이된다`() {
        val payment = newPayment()
        payment.markSuccess()
        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS)
    }

    @Test
    fun `PENDING 결제는 실패 사유와 함께 실패로 전이된다`() {
        val payment = newPayment()
        payment.markFailed(PaymentFailureReason.LIMIT_EXCEEDED)
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(payment.failureReason).isEqualTo(PaymentFailureReason.LIMIT_EXCEEDED)
    }

    @Test
    fun `이미 확정된 결제는 다시 전이할 수 없다`() {
        val payment = newPayment()
        payment.markSuccess()
        assertThatThrownBy { payment.markFailed(PaymentFailureReason.INVALID_CARD) }
            .isInstanceOf(CoreException::class.java)
    }

    @Test
    fun `요청 접수 시 transactionKey 와 acceptedAt 이 함께 기록된다`() {
        val p = newPayment()
        val now = java.time.ZonedDateTime.now()
        p.markAccepted("tx-1", now)
        assertThat(p.transactionKey).isEqualTo("tx-1")
        assertThat(p.acceptedAt).isEqualTo(now)
    }

    @Test
    fun `폴링 기록은 lastPolledAt 갱신과 시도수 증가를 누적한다`() {
        val p = newPayment()
        p.recordPoll(java.time.ZonedDateTime.now())
        p.recordPoll(java.time.ZonedDateTime.now())
        assertThat(p.pollAttempts).isEqualTo(2)
        assertThat(p.lastPolledAt).isNotNull
    }

    @Test
    fun `PENDING 결제는 환불필요로 격리될 수 있다`() {
        val p = newPayment()
        p.markRefundRequired()
        assertThat(p.status).isEqualTo(PaymentStatus.REFUND_REQUIRED)
    }

    @Test
    fun `미접수 사유로 실패 처리할 수 있다`() {
        val p = newPayment()
        p.markFailed(PaymentFailureReason.NOT_ACCEPTED)
        assertThat(p.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(p.failureReason).isEqualTo(PaymentFailureReason.NOT_ACCEPTED)
    }
}
