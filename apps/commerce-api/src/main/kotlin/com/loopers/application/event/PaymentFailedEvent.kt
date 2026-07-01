package com.loopers.application.event

data class PaymentFailedEvent(
    override val userId: Long,
    val orderId: Long,
    val transactionKey: String,
    val reason: String?,
) : UserActivityEvent {
    override val activityType: String = "PAYMENT_FAILED"
    override val description: String = "결제 실패: orderId=$orderId, transactionKey=$transactionKey, reason=$reason"
}
