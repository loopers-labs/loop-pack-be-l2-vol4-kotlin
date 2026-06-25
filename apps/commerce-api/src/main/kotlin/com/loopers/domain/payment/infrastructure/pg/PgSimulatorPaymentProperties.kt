package com.loopers.domain.payment.infrastructure.pg

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "payment.pg-simulator")
data class PgSimulatorPaymentProperties(
    val baseUrl: String = "http://localhost:8082",
    val connectTimeout: Duration = Duration.ofMillis(200),
    val readTimeout: Duration = Duration.ofMillis(700),
)
