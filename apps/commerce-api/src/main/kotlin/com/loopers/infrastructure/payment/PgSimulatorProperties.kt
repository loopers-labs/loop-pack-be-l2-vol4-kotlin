package com.loopers.infrastructure.payment

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pg-simulator")
data class PgSimulatorProperties(
    val baseUrl: String,
    val callbackUrl: String,
)
