package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCancelCommand
import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentResult
import com.loopers.application.payment.PaymentStatus
import com.loopers.application.payment.PaymentTransactionInfo
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.client.RestClient

class PgPaymentGateway(
    private val restClient: RestClient,
) : PaymentGateway {

    override fun pay(command: PaymentCommand): PaymentResult {
        val request = PgPaymentRequest(
            orderId = command.orderId.toString(),
            cardType = command.cardType,
            cardNo = command.cardNo,
            amount = command.amount.amount,
            callbackUrl = command.callbackUrl,
        )

        val response = restClient.post()
            .uri("/api/v1/payments")
            .header("X-USER-ID", command.userId.toString())
            .body(request)
            .retrieve()
            .body(PgApiResponse::class.java)
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 응답이 없습니다.")

        val data = response.data
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 응답 데이터가 없습니다.")

        return PaymentResult(
            transactionKey = data.transactionKey,
            status = PaymentStatus.valueOf(data.status),
            reason = data.reason,
        )
    }

    override fun cancel(command: PaymentCancelCommand) {
        // TODO: PG 결제 취소 API 연동
    }

    override fun getTransactionStatus(transactionKey: String): PaymentTransactionInfo {
        // TODO: PG 결제 상태 확인 API 연동
        throw UnsupportedOperationException("아직 구현되지 않았습니다.")
    }

    data class PgPaymentRequest(
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String,
    )

    data class PgApiResponse(
        val meta: PgMetadata,
        val data: PgTransactionResponse?,
    )

    data class PgMetadata(
        val result: String,
        val errorCode: String?,
        val message: String?,
    )

    data class PgTransactionResponse(
        val transactionKey: String,
        val status: String,
        val reason: String?,
    )
}
