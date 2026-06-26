package com.loopers.application.payment

/**
 * Application port for provider communication.
 *
 * Implementations may perform network I/O and must not be called from a DB transaction. Application services persist the
 * recoverable payment state before and after these calls.
 */
interface PaymentGateway {
    /** Starts provider approval for a ready payment request. */
    fun approve(command: PaymentCommand.Approve): PgResult

    /** Re-checks provider state for recovery without creating another user payment attempt. */
    fun verify(command: PaymentCommand.Verify): PgResult

    /** Reverses an approved provider transaction before local cancellation state is committed. */
    fun cancel(command: PaymentCommand.Cancel): PgResult

    /** Normalized provider response used by application services without leaking provider DTOs. */
    data class PgResult(
        val success: Boolean,
        val pgStatus: String,
        val pgTransactionId: String?,
        val approvedAmount: Long?,
        val failureReason: String?,
        val rawResponseSummary: String,
    )
}
