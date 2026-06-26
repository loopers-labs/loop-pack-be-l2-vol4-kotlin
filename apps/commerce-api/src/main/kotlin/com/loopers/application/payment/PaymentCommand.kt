package com.loopers.application.payment

import com.loopers.domain.payment.PgProvider

class PaymentCommand {
    /**
     * Starts the first provider approval attempt for a ready payment.
     *
     * This may create or confirm the provider transaction, so callers must run it outside any DB transaction and persist
     * enough local state before/after the call to recover from process failure.
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
     * Re-reads provider state for a transaction that may already have been approved.
     *
     * Verify is for recovery paths such as failed internal completion, callbacks, or batch retry; it must not represent a
     * new user payment attempt or a duplicate approval request.
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
     * Reads provider transactions already associated with an order.
     *
     * This is used after uncertain request failures, especially timeout or lost response, before sending another provider
     * payment request for the same order.
     */
    data class FindByOrder(
        val userId: Long,
        val orderId: Long,
        val pgProvider: PgProvider = PgProvider.PG_SIMULATOR,
    )

    /**
     * Requests provider-side reversal for an approved transaction.
     *
     * The local order, stock, and payment cancellation must be committed only after provider cancellation succeeds.
     */
    data class Cancel(
        val userId: Long,
        val orderId: Long,
        val pgTransactionId: String,
        val amount: Long,
        val pgProvider: PgProvider = PgProvider.PG_SIMULATOR,
    )
}
