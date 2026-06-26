package com.loopers.infrastructure.payment

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "pg-simulator")
data class PgSimulatorProperties(
    val baseUrl: String,
    val callbackUrl: String,
    val timeout: Duration = Duration.ofSeconds(10),
    val retry: RetryPolicy = RetryPolicy(),
    val circuitBreaker: CircuitBreakerPolicy = CircuitBreakerPolicy(),
) {
    data class RetryPolicy(
        val maxRetries: Int = 3,
        val waitDuration: Duration = Duration.ofMillis(100),
    )

    data class CircuitBreakerPolicy(
        val failureRateThreshold: Float = 50F,
        val slidingWindowSize: Int = 10,
        val minimumNumberOfCalls: Int = 5,
        val waitDurationInOpenState: Duration = Duration.ofSeconds(10),
        val permittedNumberOfCallsInHalfOpenState: Int = 2,
    )
}
