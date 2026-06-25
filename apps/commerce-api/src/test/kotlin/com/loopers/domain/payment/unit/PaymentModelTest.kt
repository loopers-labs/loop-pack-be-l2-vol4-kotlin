package com.loopers.domain.payment.unit

import com.loopers.domain.payment.exception.InvalidPaymentException
import com.loopers.domain.payment.model.PaymentModel
import com.loopers.domain.payment.model.PaymentStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZonedDateTime

class PaymentModelTest {
    @Test
    fun `결제는_요청_상태로_생성된다`() {
        val requestedAt = ZonedDateTime.parse("2026-06-25T10:00:00+09:00[Asia/Seoul]")

        val payment = PaymentModel.request(orderId = 1L, requestedAt = requestedAt)

        assertThat(payment.orderId).isEqualTo(1L)
        assertThat(payment.status).isEqualTo(PaymentStatus.REQUESTED)
        assertThat(payment.requestedAt).isEqualTo(requestedAt)
        assertThat(payment.completedAt).isNull()
    }

    @Test
    fun `요청_결제는_상태불명으로_전이된다`() {
        val payment = PaymentModel.request(orderId = 1L)

        val unknown = payment.markUnknown("timeout")

        assertThat(unknown.status).isEqualTo(PaymentStatus.UNKNOWN)
        assertThat(unknown.failureReason).isEqualTo("timeout")
        assertThat(unknown.completedAt).isNull()
    }

    @Test
    fun `상태불명_결제는_승인될_수_있다`() {
        val now = ZonedDateTime.parse("2026-06-25T10:01:00+09:00[Asia/Seoul]")
        val payment = PaymentModel.request(orderId = 1L).markUnknown("timeout")

        val approved = payment.approve(externalTransactionKey = "tx-1", completedAt = now)

        assertThat(approved.status).isEqualTo(PaymentStatus.APPROVED)
        assertThat(approved.failureReason).isNull()
        assertThat(approved.completedAt).isEqualTo(now)
    }

    @Test
    fun `상태불명_결제는_실패될_수_있다`() {
        val now = ZonedDateTime.parse("2026-06-25T10:01:00+09:00[Asia/Seoul]")
        val payment = PaymentModel.request(orderId = 1L).markUnknown("timeout")

        val failed = payment.fail("한도초과", now)

        assertThat(failed.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(failed.failureReason).isEqualTo("한도초과")
        assertThat(failed.completedAt).isEqualTo(now)
    }

    @Test
    fun `요청_결제는_승인되면_완료시각을_기록한다`() {
        val now = ZonedDateTime.parse("2026-06-25T10:01:00+09:00[Asia/Seoul]")
        val payment = PaymentModel.request(orderId = 1L)

        val approved = payment.approve(externalTransactionKey = "tx-1", completedAt = now)

        assertThat(approved.status).isEqualTo(PaymentStatus.APPROVED)
        assertThat(approved.externalTransactionKey).isEqualTo("tx-1")
        assertThat(approved.completedAt).isEqualTo(now)
    }

    @Test
    fun `외부_거래키는_다른_값으로_변경할_수_없다`() {
        val payment = PaymentModel.request(orderId = 1L)
            .assignTransactionKey("tx-1")

        val sameTransactionKey = payment.assignTransactionKey("tx-1")

        assertThat(sameTransactionKey).isEqualTo(payment)
        assertThrows<InvalidPaymentException> {
            payment.assignTransactionKey("tx-2")
        }
    }

    @Test
    fun `요청_결제는_실패되면_완료시각과_실패사유를_기록한다`() {
        val now = ZonedDateTime.parse("2026-06-25T10:01:00+09:00[Asia/Seoul]")
        val payment = PaymentModel.request(orderId = 1L)

        val failed = payment.fail("한도초과", now)

        assertThat(failed.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(failed.failureReason).isEqualTo("한도초과")
        assertThat(failed.completedAt).isEqualTo(now)
    }

    @Test
    fun `완료_상태의_중복_전이는_기존_완료상태를_유지한다`() {
        val completedAt = ZonedDateTime.parse("2026-06-25T10:01:00+09:00[Asia/Seoul]")
        val approved = PaymentModel.request(orderId = 1L)
            .approve(externalTransactionKey = "tx-1", completedAt = completedAt)
        val failed = PaymentModel.request(orderId = 2L)
            .fail("한도초과", completedAt)

        val duplicatedApprove = approved.approve("tx-1", completedAt.plusMinutes(1))
        val duplicatedFail = failed.fail("late failed", completedAt.plusMinutes(2))

        assertThat(duplicatedApprove).isEqualTo(approved)
        assertThat(duplicatedFail).isEqualTo(failed)
    }

    @Test
    fun `승인_결제는_실패나_상태불명으로_되돌릴_수_없다`() {
        val payment = PaymentModel.request(orderId = 1L)
            .approve(externalTransactionKey = "tx-1")

        assertThrows<InvalidPaymentException> {
            payment.fail("late failed")
        }
        assertThrows<InvalidPaymentException> {
            payment.markUnknown("timeout")
        }
    }

    @Test
    fun `실패_결제는_승인이나_상태불명으로_되돌릴_수_없다`() {
        val payment = PaymentModel.request(orderId = 1L)
            .assignTransactionKey("tx-1")
            .fail("한도초과")

        assertThrows<InvalidPaymentException> {
            payment.approve(externalTransactionKey = "tx-1")
        }
        assertThrows<InvalidPaymentException> {
            payment.markUnknown("timeout")
        }
    }
}
