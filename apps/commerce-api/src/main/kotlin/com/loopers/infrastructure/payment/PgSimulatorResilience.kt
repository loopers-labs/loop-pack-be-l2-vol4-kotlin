package com.loopers.infrastructure.payment

import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.timelimiter.TimeLimiter
import io.github.resilience4j.timelimiter.TimeLimiterConfig
import org.springframework.web.client.RestClientException
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeoutException
import java.util.function.Supplier

class PgSimulatorResilience(
    private val retry: Retry,
    private val circuitBreaker: CircuitBreaker,
    private val timeLimiter: TimeLimiter,
    private val executor: ExecutorService,
) {
    fun <T> execute(supplier: () -> T): T {
        val callable = Callable { supplier() }
        val circuitBreakerCallable = CircuitBreaker.decorateCallable(circuitBreaker, callable)
        val retryCallable = Retry.decorateCallable(retry, circuitBreakerCallable)
        val futureSupplier = Supplier<Future<T>> { executor.submit(retryCallable) }
        return timeLimiter.executeFutureSupplier(futureSupplier)
    }

    companion object {
        fun from(
            properties: PgSimulatorProperties,
            executor: ExecutorService = Executors.newCachedThreadPool(),
        ): PgSimulatorResilience {
            val retryConfig = RetryConfig.custom<Any>()
                .maxAttempts(properties.retry.maxRetries + 1)
                .intervalFunction(retryInterval(properties.retry))
                .retryExceptions(
                    IOException::class.java,
                    RestClientException::class.java,
                    TimeoutException::class.java,
                )
                .ignoreExceptions(CallNotPermittedException::class.java)
                .build()

            val circuitBreakerConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(properties.circuitBreaker.failureRateThreshold)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.circuitBreaker.slidingWindowSize)
                .minimumNumberOfCalls(properties.circuitBreaker.minimumNumberOfCalls)
                .waitDurationInOpenState(properties.circuitBreaker.waitDurationInOpenState)
                .permittedNumberOfCallsInHalfOpenState(properties.circuitBreaker.permittedNumberOfCallsInHalfOpenState)
                .recordExceptions(
                    IOException::class.java,
                    RestClientException::class.java,
                    TimeoutException::class.java,
                )
                .build()

            val timeLimiterConfig = TimeLimiterConfig.custom()
                .timeoutDuration(properties.timeout)
                .cancelRunningFuture(true)
                .build()

            return PgSimulatorResilience(
                retry = Retry.of("pgSimulator", retryConfig),
                circuitBreaker = CircuitBreaker.of("pgSimulator", circuitBreakerConfig),
                timeLimiter = TimeLimiter.of(timeLimiterConfig),
                executor = executor,
            )
        }

        private fun retryInterval(retry: PgSimulatorProperties.RetryPolicy): IntervalFunction =
            if (retry.waitDuration.isZero) {
                IntervalFunction { 0L }
            } else {
                IntervalFunction.ofRandomized(retry.waitDuration, retry.randomizationFactor)
            }
    }
}
