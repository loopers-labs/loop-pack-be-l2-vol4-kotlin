package com.loopers.application.payment.dto

import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus

data class PaymentInfo(
    val paymentId: Long,
    val orderId: Long,
    val orderNumber: String,
    val memberId: Long,
    val idempotencyKey: String,
    val amount: Long,
    val cardType: CardType,
    val cardNo: String,
    val status: PaymentStatus,
    val transactionKey: String?,
    val reason: String?,
    val orderStatus: OrderStatus?,
) {
    companion object {
        fun from(payment: Payment, orderStatus: OrderStatus? = null): PaymentInfo {
            return PaymentInfo(
                paymentId = payment.id,
                orderId = payment.orderId,
                orderNumber = payment.orderNumber,
                memberId = payment.memberId,
                idempotencyKey = payment.idempotencyKey,
                amount = payment.amount,
                cardType = payment.cardType,
                cardNo = payment.cardNo,
                status = payment.status,
                transactionKey = payment.transactionKey,
                reason = payment.reason,
                orderStatus = orderStatus,
            )
        }
    }
}
