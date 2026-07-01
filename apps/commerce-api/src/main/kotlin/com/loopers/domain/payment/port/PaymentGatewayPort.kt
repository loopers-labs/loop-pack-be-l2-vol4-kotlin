package com.loopers.domain.payment.port

data class PaymentGatewayRequest(
    val userId: Long,
    val orderId: Long,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)

data class PaymentGatewayResult(
    val transactionKey: String,
    val status: PaymentGatewayStatus,
    val reason: String?,
)

enum class PaymentGatewayStatus {
    PENDING,
    SUCCESS,
    FAILED,
}

class PaymentGatewayUnknownException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface PaymentGatewayPort {
    fun request(request: PaymentGatewayRequest): PaymentGatewayResult
    fun getTransaction(userId: Long, transactionKey: String): PaymentGatewayResult
    fun findByOrderId(userId: Long, orderId: Long): List<PaymentGatewayResult>
}
