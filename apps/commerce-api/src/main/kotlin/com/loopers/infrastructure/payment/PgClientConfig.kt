package com.loopers.infrastructure.payment

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.time.Duration

@Configuration
class PgClientConfig {

    @Bean("pgRestTemplate")
    fun pgRestTemplate(
        @Value("\${pg.connect-timeout:1000}") connectTimeout: Long,
        @Value("\${pg.read-timeout:1000}") readTimeout: Long,
    ): RestTemplate {
        return RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(connectTimeout))
            .setReadTimeout(Duration.ofMillis(readTimeout))
            .build()
    }
}
