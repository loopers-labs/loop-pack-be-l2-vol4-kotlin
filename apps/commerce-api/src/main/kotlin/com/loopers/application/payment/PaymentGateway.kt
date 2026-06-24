package com.loopers.application.payment

/**
 * Application port for PG communication; implementations translate commerce payment commands into provider-specific calls.
 */
interface PaymentGateway {
    /** Requests a PG transaction for a ready payment. */
    fun approve(command: PaymentCommand.Approve): PgResult

    /** Reads the provider state for a previously requested PG transaction. */
    fun verify(command: PaymentCommand.Verify): PgResult

    /** Requests provider-side cancellation for an approved transaction. */
    fun cancel(command: PaymentCommand.Cancel): PgResult

    /** Normalized PG response used by application services without leaking provider DTOs. */
    data class PgResult(
        val success: Boolean,
        val pgStatus: String,
        val pgTransactionId: String?,
        val approvedAmount: Long?,
        val failureReason: String?,
        val rawResponseSummary: String,
    )
}
