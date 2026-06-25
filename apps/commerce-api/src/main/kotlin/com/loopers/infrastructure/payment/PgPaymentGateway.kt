package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCancelCommand
import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentResult
import com.loopers.application.payment.PaymentStatus
import com.loopers.application.payment.PaymentTransactionInfo
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.springframework.web.client.RestClient

class PgPaymentGateway(
    private val restClient: RestClient,
    private val circuitBreaker: CircuitBreaker = CircuitBreaker.ofDefaults("pg-payment"),
    private val retry: Retry = Retry.ofDefaults("pg-payment"),
) : PaymentGateway {

    override fun pay(command: PaymentCommand): PaymentResult {
        try {
            return circuitBreaker.executeSupplier { callPg(command) }
        } catch (e: CallNotPermittedException) {
            throw CoreException(ErrorType.SERVICE_UNAVAILABLE, "결제 서비스를 일시적으로 이용할 수 없습니다.")
        }
    }

    private fun callPg(command: PaymentCommand): PaymentResult {
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
            .body(PgPaymentApiResponse::class.java)
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 응답이 없습니다.")

        val data = response.data
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 요청 응답 데이터가 없습니다.")

        return PaymentResult(
            transactionKey = data.transactionKey,
            status = toPaymentStatus(data.status),
            reason = data.reason,
        )
    }

    override fun cancel(command: PaymentCancelCommand) {
        // TODO: PG 결제 취소 API 연동
    }

    override fun getTransactionStatus(transactionKey: String): PaymentTransactionInfo {
        return retry.executeSupplier {
            val response = restClient.get()
                .uri("/api/v1/payments/{transactionKey}", transactionKey)
                .header("X-USER-ID", "system")
                .retrieve()
                .body(PgTransactionDetailApiResponse::class.java)
                ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 거래 조회 응답이 없습니다.")

            val data = response.data
                ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 거래 조회 응답 데이터가 없습니다.")

            PaymentTransactionInfo(
                transactionKey = data.transactionKey,
                orderId = data.orderId,
                cardType = data.cardType,
                cardNo = data.cardNo,
                amount = data.amount,
                status = toPaymentStatus(data.status),
                reason = data.reason,
            )
        }
    }

    override fun getTransactionsByOrderId(orderId: String): List<PaymentTransactionInfo> {
        return retry.executeSupplier {
            val response = restClient.get()
                .uri("/api/v1/payments?orderId={orderId}", orderId)
                .header("X-USER-ID", "system")
                .retrieve()
                .body(PgOrderApiResponse::class.java)
                ?: return@executeSupplier emptyList()

            val data = response.data ?: return@executeSupplier emptyList()

            data.transactions.map {
                PaymentTransactionInfo(
                    transactionKey = it.transactionKey,
                    orderId = data.orderId,
                    cardType = "",
                    cardNo = "",
                    amount = 0L,
                    status = toPaymentStatus(it.status),
                    reason = it.reason,
                )
            }
        }
    }

    companion object {
        private val PG_STATUS_MAP = mapOf(
            "PENDING" to PaymentStatus.PENDING,
            "SUCCESS" to PaymentStatus.SUCCESS,
            "FAILED" to PaymentStatus.FAILED,
        )

        fun toPaymentStatus(pgStatus: String): PaymentStatus {
            return PG_STATUS_MAP[pgStatus]
                ?: throw CoreException(ErrorType.INTERNAL_ERROR, "알 수 없는 PG 상태입니다. status=$pgStatus")
        }
    }

    data class PgPaymentRequest(
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String,
    )

    data class PgMetadata(
        val result: String,
        val errorCode: String?,
        val message: String?,
    )

    data class PgPaymentApiResponse(
        val meta: PgMetadata,
        val data: PgPaymentData?,
    )

    data class PgPaymentData(
        val transactionKey: String,
        val status: String,
        val reason: String?,
    )

    data class PgTransactionDetailApiResponse(
        val meta: PgMetadata,
        val data: PgTransactionDetailData?,
    )

    data class PgTransactionDetailData(
        val transactionKey: String,
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val status: String,
        val reason: String?,
    )

    data class PgOrderApiResponse(
        val meta: PgMetadata,
        val data: PgOrderData?,
    )

    data class PgOrderData(
        val orderId: String,
        val transactions: List<PgOrderTransactionData>,
    )

    data class PgOrderTransactionData(
        val transactionKey: String,
        val status: String,
        val reason: String?,
    )
}
