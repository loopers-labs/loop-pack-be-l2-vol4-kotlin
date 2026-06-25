package com.loopers.domain.payment.infrastructure.pg

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "payment.pg-simulator")
data class PgSimulatorPaymentProperties(
    val baseUrl: String = "http://localhost:8082",
    val connectTimeout: Duration = Duration.ofMillis(200),
    val readTimeout: Duration = Duration.ofMillis(700),
    val retryMaxAttempts: Int = 3,
    val retryInitialInterval: Duration = Duration.ofMillis(100),
    val retryMultiplier: Double = 2.0,
    val circuitBreakerSlidingWindowSize: Int = 10,
    val circuitBreakerMinimumNumberOfCalls: Int = 5,
    val circuitBreakerFailureRateThreshold: Float = 50.0f,
    val circuitBreakerWaitDurationInOpenState: Duration = Duration.ofSeconds(5),
    val circuitBreakerPermittedCallsInHalfOpenState: Int = 2,
)
