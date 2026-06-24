package com.loopers.infrastructure.payment.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "pg.payment")
data class PgPaymentProperties(
    val baseUrl: String,
    val callbackUrl: String,
    val connectTimeoutMillis: Int,
    val readTimeoutMillis: Int,
)
