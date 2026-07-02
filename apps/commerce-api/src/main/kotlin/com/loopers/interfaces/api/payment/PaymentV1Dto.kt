package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentInfo
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgTransactionStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

object PaymentV1Dto {
    data class PayRequest(
        @field:NotBlank(message = "주문 ID는 필수입니다.")
        @field:Pattern(regexp = "^\\d+$", message = "주문 ID는 숫자여야 합니다.")
        val orderId: String,
        val cardType: CardType,
        @field:NotBlank(message = "카드 번호는 필수입니다.")
        @field:Pattern(
            regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}$",
            message = "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.",
        )
        val cardNo: String,
    )

    data class PaymentResponse(
        val paymentId: Long,
        val orderId: Long,
        val cardType: CardType,
        val amount: Long,
        val status: PaymentStatus,
        val transactionKey: String?,
        val reason: String?,
    ) {
        companion object {
            fun from(info: PaymentInfo): PaymentResponse = PaymentResponse(
                paymentId = info.id,
                orderId = info.orderId,
                cardType = info.cardType,
                amount = info.amount,
                status = info.status,
                transactionKey = info.transactionKey,
                reason = info.reason,
            )
        }
    }

    /**
     * PG 콜백 페이로드. pg-simulator 의 TransactionInfo 구조를 그대로 수신한다.
     */
    data class CallbackRequest(
        val transactionKey: String,
        val orderId: String?,
        val status: PgTransactionStatus,
        val reason: String?,
    )
}
