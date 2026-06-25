package com.loopers.domain.payment.support

import com.loopers.domain.payment.application.command.PaymentCallbackCommand
import com.loopers.domain.payment.application.command.PaymentRequestCommand
import com.loopers.domain.payment.model.PaymentModel
import com.loopers.domain.payment.model.PaymentStatus
import java.time.ZonedDateTime

class PaymentSteps {
    companion object {
        const val 기본_결제_ID: Long = 100L
        const val 기본_주문_ID: Long = 10L
        const val 기본_사용자_ID: Long = 1L
        const val 기본_거래키: String = "20260625:TR:test01"

        fun 결제_도메인_생성(
            id: Long = 기본_결제_ID,
            orderId: Long = 기본_주문_ID,
            externalTransactionKey: String? = 기본_거래키,
            status: PaymentStatus = PaymentStatus.REQUESTED,
            failureReason: String? = null,
            requestedAt: ZonedDateTime = ZonedDateTime.parse("2026-06-25T10:00:00+09:00[Asia/Seoul]"),
            completedAt: ZonedDateTime? = null,
        ): PaymentModel = PaymentModel(
            id = id,
            orderId = orderId,
            externalTransactionKey = externalTransactionKey,
            status = status,
            failureReason = failureReason,
            requestedAt = requestedAt,
            completedAt = completedAt,
        )

        fun 결제_요청_커맨드(
            userId: Long = 기본_사용자_ID,
            orderId: Long = 기본_주문_ID,
            cardType: String = "SAMSUNG",
            cardNo: String = "1234-5678-1234-5678",
        ): PaymentRequestCommand = PaymentRequestCommand(
            userId = userId,
            orderId = orderId,
            cardType = cardType,
            cardNo = cardNo,
        )

        fun 결제_콜백_커맨드(
            transactionKey: String = 기본_거래키,
            status: String = "SUCCESS",
            reason: String? = null,
        ): PaymentCallbackCommand = PaymentCallbackCommand(
            transactionKey = transactionKey,
            status = status,
            reason = reason,
        )
    }
}
