package com.loopers.application.event

data class PaymentCompletedEvent(
    override val userId: Long,
    val orderId: Long,
    val transactionKey: String,
    val amount: Long,
) : UserActivityEvent {
    override val activityType: String = "PAYMENT_SUCCESS"
    override val description: String = "결제 성공: orderId=$orderId, transactionKey=$transactionKey, amount=$amount"
}
