package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class PaymentTest {
    @Test
    fun readyPaymentStoresOrderProviderRequestIdAndAmount() {
        val payment = Payment(
            orderId = 1L,
            pgProvider = PgProvider.FAKE,
            paymentRequestId = "pay-req-1",
            requestedAmount = 3000L,
        )

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.READY) },
            { assertThat(payment.completionRetryCount).isZero() },
            { assertThat(payment.paymentKey).isNull() },
        )
    }

    @Test
    fun approveRequestedStoresPaymentKey() {
        val payment = readyPayment()

        payment.recordApproveRequested("payment-key-1")

        assertThat(payment.paymentKey).isEqualTo("payment-key-1")
    }

    @Test
    fun approveStoresPgTransactionAndApprovedAmount() {
        val payment = readyPayment()
        payment.recordApproveRequested("payment-key-1")

        payment.approve(pgTransactionId = "pg-tx-1", approvedAmount = 3000L)

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED) },
            { assertThat(payment.pgTransactionId).isEqualTo("pg-tx-1") },
            { assertThat(payment.approvedAmount).isEqualTo(3000L) },
            { assertThat(payment.approvedAt).isNotNull() },
        )
    }

    @Test
    fun completionFailureIncrementsOnlyWhenRequested() {
        val payment = readyPayment()
        payment.markCompletionFailed("stock conflict")

        payment.incrementCompletionRetryFailure("stock conflict again")

        assertAll(
            { assertThat(payment.status).isEqualTo(PaymentStatus.COMPLETION_FAILED) },
            { assertThat(payment.completionRetryCount).isEqualTo(1) },
            { assertThat(payment.failureReason).isEqualTo("stock conflict again") },
        )
    }

    @Test
    fun negativeRequestedAmountThrowsBadRequest() {
        val ex = assertThrows<CoreException> {
            Payment(
                orderId = 1L,
                pgProvider = PgProvider.FAKE,
                paymentRequestId = "pay-req-1",
                requestedAmount = -1L,
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    private fun readyPayment() = Payment(
        orderId = 1L,
        pgProvider = PgProvider.FAKE,
        paymentRequestId = "pay-req-1",
        requestedAmount = 3000L,
    )
}
