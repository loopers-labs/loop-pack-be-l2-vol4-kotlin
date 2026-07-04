package com.loopers.domain.payment.port

interface PaymentGatewayPort {
    fun request(request: PaymentGatewayRequest): PaymentGatewayResult
    fun getTransaction(userId: Long, transactionKey: String): PaymentGatewayResult
    fun findByOrderId(userId: Long, orderId: Long): List<PaymentGatewayResult>
}
