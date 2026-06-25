package com.loopers.infrastructure.payment

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class PaymentGatewayConfig {
    @Bean
    @Primary
    @Profile("!test")
    fun pgPaymentGateway(
        @Value("\${pg.base-url:http://localhost:8082}") baseUrl: String,
    ): PgPaymentGateway {
        return createPgPaymentGateway(baseUrl)
    }

    fun createPgPaymentGateway(baseUrl: String): PgPaymentGateway {
        val settings = ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(Duration.ofSeconds(1))
            .withReadTimeout(Duration.ofSeconds(3))

        val restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .build()

        val circuitBreakerConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50f)
            .slidingWindowSize(5)
            .minimumNumberOfCalls(5)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(2)
            .build()

        val circuitBreaker = CircuitBreakerRegistry.of(circuitBreakerConfig)
            .circuitBreaker("pg-payment")

        val retryConfig = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(
                ResourceAccessException::class.java,
                HttpServerErrorException::class.java,
            )
            .ignoreExceptions(
                HttpClientErrorException::class.java,
            )
            .build()

        val retry = RetryRegistry.of(retryConfig)
            .retry("pg-payment")

        return PgPaymentGateway(restClient, circuitBreaker, retry)
    }
}
