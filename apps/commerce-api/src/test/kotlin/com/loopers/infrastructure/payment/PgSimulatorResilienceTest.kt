package com.loopers.infrastructure.payment

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.core.functions.Either
import io.github.resilience4j.retry.Retry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.RestClientException
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PgSimulatorResilienceTest {
    @Test
    fun buildsCountBasedCircuitBreakerForPaymentGateway() {
        val resilience = PgSimulatorResilience.from(
            PgSimulatorProperties(
                baseUrl = "http://pg.local",
                callbackUrl = "http://localhost:8080/api/v1/payments/callback",
            ),
        )

        val config = resilience.circuitBreaker().circuitBreakerConfig

        assertThat(config.slidingWindowType).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
        assertThat(config.slidingWindowSize).isEqualTo(50)
        assertThat(config.minimumNumberOfCalls).isEqualTo(10)
        assertThat(config.failureRateThreshold).isEqualTo(10F)
    }

    @Test
    fun buildsRetryWithRandomizedJitterAroundBaseWait() {
        val resilience = PgSimulatorResilience.from(
            PgSimulatorProperties(
                baseUrl = "http://pg.local",
                callbackUrl = "http://localhost:8080/api/v1/payments/callback",
            ),
        )

        val intervalFunction = resilience.retry().retryConfig.getIntervalBiFunction<Unit>()
        val intervals = (1..20).map {
            intervalFunction.apply(it, Either.left(RestClientException("retryable")))
        }

        assertThat(intervals).allSatisfy { interval ->
            assertThat(interval).isBetween(50L, 150L)
        }
        assertThat(intervals.toSet()).hasSizeGreaterThan(1)
    }

    @Test
    fun circuitBreakerEvaluatesEachRetryAttempt() {
        val executor = Executors.newSingleThreadExecutor()
        val resilience = PgSimulatorResilience.from(
            PgSimulatorProperties(
                baseUrl = "http://pg.local",
                callbackUrl = "http://localhost:8080/api/v1/payments/callback",
                timeout = Duration.ofSeconds(1),
                retry = PgSimulatorProperties.RetryPolicy(
                    maxRetries = 3,
                    waitDuration = Duration.ZERO,
                ),
                circuitBreaker = PgSimulatorProperties.CircuitBreakerPolicy(
                    failureRateThreshold = 50F,
                    slidingWindowSize = 2,
                    minimumNumberOfCalls = 2,
                    waitDurationInOpenState = Duration.ofSeconds(10),
                    permittedNumberOfCallsInHalfOpenState = 1,
                ),
            ),
            executor,
        )
        val attempts = AtomicInteger()

        assertThrows<CallNotPermittedException> {
            resilience.execute<Unit> {
                attempts.incrementAndGet()
                throw RestClientException("pg down")
            }
        }

        assertThat(attempts.get()).isEqualTo(2)
        assertThat(resilience.circuitBreaker().state).isEqualTo(CircuitBreaker.State.OPEN)
        executor.shutdown()
        executor.awaitTermination(1, TimeUnit.SECONDS)
    }

    private fun PgSimulatorResilience.retry(): Retry {
        val field = PgSimulatorResilience::class.java.getDeclaredField("retry")
        field.isAccessible = true
        return field.get(this) as Retry
    }

    private fun PgSimulatorResilience.circuitBreaker(): CircuitBreaker {
        val field = PgSimulatorResilience::class.java.getDeclaredField("circuitBreaker")
        field.isAccessible = true
        return field.get(this) as CircuitBreaker
    }
}
