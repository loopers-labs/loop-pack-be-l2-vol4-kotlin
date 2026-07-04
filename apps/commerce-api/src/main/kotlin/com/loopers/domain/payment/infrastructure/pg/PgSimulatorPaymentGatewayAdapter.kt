package com.loopers.domain.payment.infrastructure.pg

import com.loopers.domain.payment.constant.PaymentErrorMessages
import com.loopers.domain.payment.port.PaymentGatewayPort
import com.loopers.domain.payment.port.PaymentGatewayRequest
import com.loopers.domain.payment.port.PaymentGatewayResult
import com.loopers.domain.payment.port.PaymentGatewayUnknownException
import com.loopers.domain.payment.infrastructure.pg.dto.PgApiResponse
import com.loopers.domain.payment.infrastructure.pg.dto.PgOrderResponse
import com.loopers.domain.payment.infrastructure.pg.dto.PgPaymentRequest
import com.loopers.domain.payment.infrastructure.pg.dto.PgTransactionDetailResponse
import com.loopers.domain.payment.infrastructure.pg.dto.PgTransactionResponse
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import java.util.concurrent.CancellationException

@Component
class PgSimulatorPaymentGatewayAdapter(
    restTemplateBuilder: RestTemplateBuilder,
    private val properties: PgSimulatorPaymentProperties,
) : PaymentGatewayPort {
    private val restTemplate = restTemplateBuilder
        .connectTimeout(properties.connectTimeout)
        .readTimeout(properties.readTimeout)
        .build()
    private val retry = Retry.of("pg-simulator", retryConfig())
    private val circuitBreaker = CircuitBreaker.of("pg-simulator", circuitBreakerConfig())

    override fun request(request: PaymentGatewayRequest): PaymentGatewayResult =
        executeWithResilience {
            exchange<PgTransactionResponse>(
                path = "/api/v1/payments",
                method = HttpMethod.POST,
                userId = request.userId,
                body = PgPaymentRequest.from(request),
            ).toGatewayResult()
        }

    override fun getTransaction(userId: Long, transactionKey: String): PaymentGatewayResult =
        executeWithResilience {
            exchange<PgTransactionDetailResponse>(
                path = "/api/v1/payments/$transactionKey",
                method = HttpMethod.GET,
                userId = userId,
                body = null,
            ).toGatewayResult()
        }

    override fun findByOrderId(userId: Long, orderId: Long): List<PaymentGatewayResult> =
        executeWithResilience {
            exchange<PgOrderResponse>(
                path = "/api/v1/payments?orderId=$orderId",
                method = HttpMethod.GET,
                userId = userId,
                body = null,
            ).transactions.map { it.toGatewayResult() }
        }

    private fun <T> executeWithResilience(block: () -> T): T =
        try {
            circuitBreaker.executeSupplier {
                retry.executeSupplier {
                    block()
                }
            }
        } catch (e: CallNotPermittedException) {
            throw PaymentGatewayUnknownException(PaymentErrorMessages.PG_CIRCUIT_OPEN, e)
        }

    private fun retryConfig(): RetryConfig =
        RetryConfig.custom<Any>()
            .maxAttempts(properties.retryMaxAttempts)
            .intervalFunction(
                IntervalFunction.ofExponentialBackoff(
                    properties.retryInitialInterval.toMillis(),
                    properties.retryMultiplier,
                ),
            )
            .retryOnException { throwable ->
                throwable is PaymentGatewayUnknownException && throwable.cause !is HttpClientErrorException
            }
            .build()

    private fun circuitBreakerConfig(): CircuitBreakerConfig =
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(properties.circuitBreakerSlidingWindowSize)
            .minimumNumberOfCalls(properties.circuitBreakerMinimumNumberOfCalls)
            .failureRateThreshold(properties.circuitBreakerFailureRateThreshold)
            .waitDurationInOpenState(properties.circuitBreakerWaitDurationInOpenState)
            .permittedNumberOfCallsInHalfOpenState(properties.circuitBreakerPermittedCallsInHalfOpenState)
            .recordException { throwable ->
                throwable is PaymentGatewayUnknownException && throwable.cause !is HttpClientErrorException
            }
            .build()

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
            throw PaymentGatewayUnknownException(PaymentErrorMessages.PG_STATUS_UNCONFIRMED, e)
        } catch (e: CancellationException) {
            throw PaymentGatewayUnknownException(PaymentErrorMessages.PG_STATUS_UNCONFIRMED, e)
        }
        return response.body?.data ?: throw PaymentGatewayUnknownException(PaymentErrorMessages.PG_RESPONSE_NO_DATA)
    }
}
