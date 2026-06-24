package com.loopers.infrastructure.payment.client

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PgTransactionStatus
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.net.SocketTimeoutException

@Component
class PgPaymentClient(
    private val properties: PgPaymentProperties,
    circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    private val circuitBreaker = circuitBreakerRegistry.circuitBreaker("pgPayment")
    private val restClient = RestClient.builder()
        .baseUrl(properties.baseUrl)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(properties.connectTimeoutMillis)
                setReadTimeout(properties.readTimeoutMillis)
            },
        )
        .build()

    fun request(command: PgPaymentCommand.Request): PgPaymentResult {
        return runCatching {
            circuitBreaker.executeSupplier {
                requestPayment(command)
            }
        }.getOrElse { throwable ->
            when {
                throwable is CallNotPermittedException -> throw PgPaymentCircuitOpenException(throwable)
                throwable.isTimeout() -> throw PgPaymentTimeoutException(throwable)
                throwable is RestClientException -> throw PgPaymentRequestException(throwable)
                else -> throw PgPaymentRequestException(throwable)
            }
        }
    }

    fun findTransactionsByOrder(command: PgPaymentCommand.FindByOrder): List<PgPaymentResult> {
        return runCatching {
            circuitBreaker.executeSupplier {
                findPayments(command)
            }
        }.getOrElse { throwable ->
            when {
                throwable is CallNotPermittedException -> throw PgPaymentCircuitOpenException(throwable)
                throwable.isTimeout() -> throw PgPaymentTimeoutException(throwable)
                throwable is RestClientException -> throw PgPaymentRequestException(throwable)
                else -> throw PgPaymentRequestException(throwable)
            }
        }
    }

    private fun requestPayment(command: PgPaymentCommand.Request): PgPaymentResult {
        val response = restClient.post()
            .uri("/api/v1/payments")
            .header("X-USER-ID", command.userId)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(
                PgPaymentRequest(
                    orderId = command.orderNumber,
                    cardType = command.cardType,
                    cardNo = command.cardNo,
                    amount = command.amount,
                    callbackUrl = properties.callbackUrl,
                ),
            )
            .retrieve()
            .body(object : ParameterizedTypeReference<PgApiResponse<PgTransactionResponse>>() {})
            ?: throw PgPaymentRequestException(IllegalStateException("PG response body is empty."))

        val data = response.data
            ?: throw PgPaymentRequestException(IllegalStateException("PG response data is empty."))

        return PgPaymentResult(
            transactionKey = data.transactionKey,
            status = data.status,
            reason = data.reason,
        )
    }

    private fun findPayments(command: PgPaymentCommand.FindByOrder): List<PgPaymentResult> {
        val response = restClient.get()
            .uri { builder ->
                builder.path("/api/v1/payments")
                    .queryParam("orderId", command.orderNumber)
                    .build()
            }
            .header("X-USER-ID", command.userId)
            .retrieve()
            .body(object : ParameterizedTypeReference<PgApiResponse<PgOrderResponse>>() {})
            ?: throw PgPaymentRequestException(IllegalStateException("PG response body is empty."))

        return response.data
            ?.transactions
            ?.map {
                PgPaymentResult(
                    transactionKey = it.transactionKey,
                    status = it.status,
                    reason = it.reason,
                )
            }
            ?: emptyList()
    }

    private fun Throwable.isTimeout(): Boolean {
        return this is ResourceAccessException &&
            generateSequence(this as Throwable?) { it.cause }
                .any { it is SocketTimeoutException }
    }

    private data class PgPaymentRequest(
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String,
    )

    private data class PgApiResponse<T>(
        val data: T?,
    )

    private data class PgTransactionResponse(
        val transactionKey: String,
        val status: PgTransactionStatus,
        val reason: String?,
    )

    private data class PgOrderResponse(
        val orderId: String,
        val transactions: List<PgTransactionResponse>,
    )
}

object PgPaymentCommand {
    data class Request(
        val userId: String,
        val orderNumber: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
    )

    data class FindByOrder(
        val userId: String,
        val orderNumber: String,
    )
}

data class PgPaymentResult(
    val transactionKey: String,
    val status: PgTransactionStatus,
    val reason: String?,
)

class PgPaymentCircuitOpenException(cause: Throwable) : RuntimeException(cause)

class PgPaymentTimeoutException(cause: Throwable) : RuntimeException(cause)

class PgPaymentRequestException(cause: Throwable) : RuntimeException(cause)
