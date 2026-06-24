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
}
