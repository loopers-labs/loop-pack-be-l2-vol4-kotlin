package com.loopers.application.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentStatus

data class PaymentInfo(
    val id: Long,
    val orderId: Long,
    val cardType: CardType,
    val amount: Long,
    val status: PaymentStatus,
    val transactionKey: String?,
    val reason: String?,
) {
    companion object {
        fun from(payment: PaymentModel): PaymentInfo = PaymentInfo(
            id = payment.id,
            orderId = payment.orderId,
            cardType = payment.cardType,
            amount = payment.amount,
            status = payment.status,
            transactionKey = payment.transactionKey,
            reason = payment.reason,
        )
    }
}
