package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentInfo
import com.loopers.application.payment.RequestPaymentCommand
import com.loopers.application.payment.SyncPaymentResultCommand
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgStatus

class PaymentV1Dto {
    data class PaymentRequest(
        val orderId: Long,
        val cardType: CardType,
        val cardNo: String,
    ) {
        fun toCommand(loginId: String, password: String) = RequestPaymentCommand(
            loginId = loginId,
            password = password,
            orderId = orderId,
            cardType = cardType,
            cardNo = cardNo,
        )
    }

    data class PaymentResponse(
        val paymentId: Long,
        val orderId: Long,
        val status: PaymentStatus,
        val transactionKey: String?,
    ) {
        companion object {
            fun from(info: PaymentInfo) = PaymentResponse(
                paymentId = info.paymentId,
                orderId = info.orderId,
                status = info.status,
                transactionKey = info.transactionKey,
            )
        }
    }

    // PG → 우리. status/reason 은 raw 문자열로 받아 도메인으로 변환.
    data class CallbackRequest(
        val transactionKey: String,
        val orderId: Long,
        val amount: Long,
        val status: String,
        val reason: String? = null,
    ) {
        fun toCommand(): SyncPaymentResultCommand {
            val pgStatus = when (status.uppercase()) {
                "SUCCESS" -> PgStatus.SUCCESS
                "FAILED" -> PgStatus.FAILED
                else -> PgStatus.PENDING
            }
            val failureReason = if (pgStatus == PgStatus.FAILED) PaymentFailureReason.fromPgReason(reason) else null
            return SyncPaymentResultCommand(
                transactionKey = transactionKey,
                orderId = orderId,
                amount = amount,
                status = pgStatus,
                failureReason = failureReason,
            )
        }
    }
}
