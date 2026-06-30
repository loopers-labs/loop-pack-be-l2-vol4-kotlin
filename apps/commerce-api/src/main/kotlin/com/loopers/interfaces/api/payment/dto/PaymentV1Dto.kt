package com.loopers.interfaces.api.payment.dto

import com.loopers.application.payment.dto.PaymentCommand
import com.loopers.application.payment.dto.PaymentInfo
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus

object PaymentV1Dto {
    data class CallbackRequest(
        val transactionKey: String,
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val status: com.loopers.domain.payment.PgTransactionStatus,
        val reason: String?,
    ) {
        fun toCommand(): PaymentCommand.Callback {
            return PaymentCommand.Callback(
                transactionKey = transactionKey,
                orderNumber = orderId,
                cardType = cardType,
                cardNo = cardNo,
                amount = amount,
                status = status,
                reason = reason,
            )
        }
    }

    data class PaymentRequest(
        val orderId: Long,
        val cardType: CardType,
        val cardNo: String,
    ) {
        fun toCommand(idempotencyKey: String): PaymentCommand.Request {
            return PaymentCommand.Request(
                orderId = orderId,
                cardType = cardType,
                cardNo = cardNo,
                idempotencyKey = idempotencyKey,
            )
        }
    }

    data class PaymentResponse(
        val paymentId: Long,
        val orderId: Long,
        val orderNumber: String,
        val amount: Long,
        val status: PaymentStatus,
        val transactionKey: String?,
        val reason: String?,
        val orderStatus: OrderStatus?,
    ) {
        companion object {
            fun from(info: PaymentInfo): PaymentResponse {
                return PaymentResponse(
                    paymentId = info.paymentId,
                    orderId = info.orderId,
                    orderNumber = info.orderNumber,
                    amount = info.amount,
                    status = info.status,
                    transactionKey = info.transactionKey,
                    reason = info.reason,
                    orderStatus = info.orderStatus,
                )
            }
        }
    }
}
