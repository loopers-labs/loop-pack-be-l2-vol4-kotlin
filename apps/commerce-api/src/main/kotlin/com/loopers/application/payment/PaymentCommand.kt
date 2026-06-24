package com.loopers.application.payment

import com.loopers.domain.payment.PgProvider

class PaymentCommand {
    /**
     * Approve starts a PG simulator transaction for a ready payment.
     */
    data class Approve(
        val userId: Long,
        val orderId: Long,
        val paymentRequestId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val pgProvider: PgProvider = PgProvider.PG_SIMULATOR,
    )

    /**
     * Verify reads the PG simulator's current state for an existing transaction.
     */
    data class Verify(
        val userId: Long,
        val orderId: Long,
        val paymentRequestId: String,
        val paymentKey: String?,
        val pgTransactionId: String?,
        val amount: Long,
        val pgProvider: PgProvider = PgProvider.PG_SIMULATOR,
    )

    /**
     * Cancel requests PG-side reversal for an already completed transaction.
     */
    data class Cancel(
        val userId: Long,
        val orderId: Long,
        val pgTransactionId: String,
        val amount: Long,
        val pgProvider: PgProvider = PgProvider.PG_SIMULATOR,
    )
}
