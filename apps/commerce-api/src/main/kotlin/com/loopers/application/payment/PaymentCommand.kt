package com.loopers.application.payment

import com.loopers.domain.payment.PgProvider

class PaymentCommand {
    /**
     * Approve는 사용자가 PG 결제를 마친 직후, 클라이언트가 전달한 paymentKey를 서버가 PG에 제출해
     * 이 결제를 우리 주문의 결제로 승인해도 되는지 확인하고 승인 처리하는 요청이다.
     */
    data class Approve(
        val orderId: Long,
        val paymentRequestId: String,
        val paymentKey: String,
        val amount: Long,
        val pgProvider: PgProvider = PgProvider.FAKE,
    )

    /**
     * Verify는 이미 승인되었거나 승인되었을 가능성이 있는 결제 건을 기준으로 PG의 현재 결제 상태와
     * 금액, 주문 식별자를 재검증하는 요청이다. 새 결제를 만들거나 중복 승인하는 목적이 아니다.
     */
    data class Verify(
        val orderId: Long,
        val paymentRequestId: String,
        val paymentKey: String?,
        val pgTransactionId: String?,
        val amount: Long,
        val pgProvider: PgProvider = PgProvider.FAKE,
    )

    data class Cancel(
        val orderId: Long,
        val pgTransactionId: String,
        val amount: Long,
        val pgProvider: PgProvider = PgProvider.FAKE,
    )
}
