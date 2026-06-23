package com.loopers.domain.payment

/**
 * 외부 결제 게이트웨이(PG)와의 연동을 추상화한 아웃바운드 포트.
 * 콜백 URL 등 PG 고유의 연동 세부사항은 어댑터(infrastructure)의 책임이다.
 */
interface PaymentGatewayPort {
    fun requestPayment(request: PaymentGatewayRequest): PaymentGatewayResponse
}

data class PaymentGatewayRequest(
    val userId: Long,
    val orderId: Long,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
)

data class PaymentGatewayResponse(
    val transactionKey: String,
    val status: PaymentStatus,
)
