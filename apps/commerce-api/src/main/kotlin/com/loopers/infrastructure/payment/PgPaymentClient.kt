package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentRequest
import com.loopers.domain.payment.PgPaymentResponse
import com.loopers.domain.payment.PgTransactionDetail
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class PgPaymentClient(
    @Value("\${pg.base-url}") private val baseUrl: String,
    @Qualifier("pgRestTemplate") private val restTemplate: RestTemplate,
) : PgClient {

    private val log = LoggerFactory.getLogger(javaClass)

    @Retry(name = "pg")
    @CircuitBreaker(name = "pg", fallbackMethod = "requestPaymentFallback")
    override fun requestPayment(userId: Long, request: PgPaymentRequest): PgPaymentResponse {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-USER-ID", userId.toString())
        }
        val entity = HttpEntity(request, headers)

        val responseType = object : ParameterizedTypeReference<PgApiResponse<PgTransactionResponseBody>>() {}
        val response = restTemplate.exchange(
            "$baseUrl/api/v1/payments",
            HttpMethod.POST,
            entity,
            responseType,
        )
        val body = response.body?.data
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 응답이 비어있습니다.")

        return PgPaymentResponse(
            transactionKey = body.transactionKey,
            status = body.status,
            reason = body.reason,
        )
    }

    @Retry(name = "pg")
    @CircuitBreaker(name = "pg", fallbackMethod = "getTransactionStatusFallback")
    override fun getTransactionStatus(userId: Long, transactionKey: String): PgTransactionDetail {
        val headers = HttpHeaders().apply {
            set("X-USER-ID", userId.toString())
        }
        val entity = HttpEntity<Void>(headers)

        val responseType = object : ParameterizedTypeReference<PgApiResponse<PgTransactionDetailBody>>() {}
        val response = restTemplate.exchange(
            "$baseUrl/api/v1/payments/$transactionKey",
            HttpMethod.GET,
            entity,
            responseType,
        )
        val body = response.body?.data
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 응답이 비어있습니다.")

        return PgTransactionDetail(
            transactionKey = body.transactionKey,
            orderId = body.orderId,
            amount = body.amount,
            status = body.status,
            reason = body.reason,
        )
    }

    @Suppress("unused")
    fun requestPaymentFallback(userId: Long, request: PgPaymentRequest, ex: Exception): PgPaymentResponse {
        log.warn("PG 결제 요청 fallback (orderId={}): {}", request.orderId, ex.message)
        throw CoreException(ErrorType.INTERNAL_ERROR, "결제 시스템이 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.")
    }

    @Suppress("unused")
    fun getTransactionStatusFallback(userId: Long, transactionKey: String, ex: Exception): PgTransactionDetail {
        log.warn("PG 상태 조회 fallback (transactionKey={}): {}", transactionKey, ex.message)
        throw CoreException(ErrorType.INTERNAL_ERROR, "결제 상태 조회가 일시적으로 불가능합니다. 잠시 후 다시 시도해주세요.")
    }

    data class PgApiResponse<T>(
        val meta: Map<String, Any>?,
        val data: T?,
    )

    data class PgTransactionResponseBody(
        val transactionKey: String,
        val status: String,
        val reason: String?,
    )

    data class PgTransactionDetailBody(
        val transactionKey: String,
        val orderId: String,
        val cardType: String,
        val cardNo: String,
        val amount: Long,
        val status: String,
        val reason: String?,
    )
}
