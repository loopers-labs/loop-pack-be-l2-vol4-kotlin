package com.loopers.application.payment

interface PaymentGateway {
    fun approve(command: PaymentCommand.Approve): PgResult

    fun verify(command: PaymentCommand.Verify): PgResult

    fun cancel(command: PaymentCommand.Cancel): PgResult

    data class PgResult(
        val success: Boolean,
        val pgStatus: String,
        val pgTransactionId: String?,
        val approvedAmount: Long?,
        val failureReason: String?,
        val rawResponseSummary: String,
    )
}
