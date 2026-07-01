package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PayCommand
import com.loopers.application.payment.PaymentCallbackCommand
import com.loopers.application.payment.PaymentResult
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object PaymentV1Dto {
    data class PaymentRequest(
        val orderId: String,
        val cardType: String,
        val cardNo: String,
    ) {
        fun toCommand(userId: Long): PayCommand = PayCommand(
            userId = userId,
            orderId = orderId.toLongOrNull()
                ?: throw CoreException(ErrorType.BAD_REQUEST, "주문 ID 형식이 올바르지 않습니다: $orderId"),
            cardType = cardType,
            cardNo = cardNo,
        )
    }

    data class PaymentResponse(
        val transactionKey: String,
        val orderId: Long,
        val amount: Long,
        val status: String,
    ) {
        companion object {
            fun from(result: PaymentResult): PaymentResponse = PaymentResponse(
                transactionKey = result.transactionKey,
                orderId = result.orderId,
                amount = result.amount,
                status = result.status.name,
            )
        }
    }

    data class PaymentCallbackRequest(
        val transactionKey: String,
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val status: String,
        val reason: String?,
    ) {
        fun toCommand(): PaymentCallbackCommand = PaymentCallbackCommand(
            transactionKey = transactionKey,
            status = toPaymentStatus(status),
            reason = reason,
        )
    }

    private fun toPaymentStatus(value: String): PaymentStatus =
        runCatching { PaymentStatus.valueOf(value.uppercase()) }
            .getOrElse { throw CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 결제 상태입니다: $value") }
}
