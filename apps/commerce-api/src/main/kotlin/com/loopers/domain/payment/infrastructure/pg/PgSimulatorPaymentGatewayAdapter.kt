package com.loopers.domain.payment.infrastructure.pg

import com.loopers.domain.payment.port.PaymentGatewayPort
import com.loopers.domain.payment.port.PaymentGatewayRequest
import com.loopers.domain.payment.port.PaymentGatewayResult
import com.loopers.domain.payment.port.PaymentGatewayStatus
import com.loopers.domain.payment.port.PaymentGatewayUnknownException
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException

@Component
class PgSimulatorPaymentGatewayAdapter(
    restTemplateBuilder: RestTemplateBuilder,
    private val properties: PgSimulatorPaymentProperties,
) : PaymentGatewayPort {
    private val restTemplate = restTemplateBuilder
        .connectTimeout(properties.connectTimeout)
        .readTimeout(properties.readTimeout)
        .build()

    override fun request(request: PaymentGatewayRequest): PaymentGatewayResult =
        exchange<PgTransactionResponse>(
            path = "/api/v1/payments",
            method = HttpMethod.POST,
            userId = request.userId,
            body = PgPaymentRequest.from(request),
        ).toGatewayResult()

    override fun getTransaction(userId: Long, transactionKey: String): PaymentGatewayResult =
        exchange<PgTransactionDetailResponse>(
            path = "/api/v1/payments/$transactionKey",
            method = HttpMethod.GET,
            userId = userId,
            body = null,
        ).toGatewayResult()

    override fun findByOrderId(userId: Long, orderId: Long): List<PaymentGatewayResult> =
        exchange<PgOrderResponse>(
            path = "/api/v1/payments?orderId=$orderId",
            method = HttpMethod.GET,
            userId = userId,
            body = null,
        ).transactions.map { it.toGatewayResult() }

    private inline fun <reified T> exchange(
        path: String,
        method: HttpMethod,
        userId: Long,
        body: Any?,
    ): T {
        val headers = HttpHeaders()
        headers.set("X-USER-ID", userId.toString())
        val entity = HttpEntity(body, headers)
        val responseType = object : ParameterizedTypeReference<PgApiResponse<T>>() {}
        val response = try {
            restTemplate.exchange(
                "${properties.baseUrl}$path",
                method,
                entity,
                responseType,
            )
        } catch (e: RestClientException) {
            throw PaymentGatewayUnknownException("PG 결제 상태를 확정할 수 없습니다.", e)
        }
        return response.body?.data ?: throw PaymentGatewayUnknownException("PG 응답에 데이터가 없습니다.")
    }
}

private data class PgApiResponse<T>(
    val data: T?,
)

private data class PgPaymentRequest(
    val orderId: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
) {
    companion object {
        fun from(request: PaymentGatewayRequest): PgPaymentRequest = PgPaymentRequest(
            orderId = request.orderId.toString(),
            cardType = request.cardType,
            cardNo = request.cardNo,
            amount = request.amount,
            callbackUrl = request.callbackUrl,
        )
    }
}

private data class PgTransactionResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
) {
    fun toGatewayResult(): PaymentGatewayResult = PaymentGatewayResult(
        transactionKey = transactionKey,
        status = PaymentGatewayStatus.valueOf(status),
        reason = reason,
    )
}

private data class PgTransactionDetailResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
) {
    fun toGatewayResult(): PaymentGatewayResult = PaymentGatewayResult(
        transactionKey = transactionKey,
        status = PaymentGatewayStatus.valueOf(status),
        reason = reason,
    )
}

private data class PgOrderResponse(
    val transactions: List<PgTransactionResponse>,
)
