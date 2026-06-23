package com.loopers.application.payment

import com.loopers.domain.payment.PaymentRepositoryPort
import com.loopers.domain.payment.PaymentService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PaymentConfig {
    @Bean
    fun paymentService(paymentRepositoryPort: PaymentRepositoryPort): PaymentService =
        PaymentService(paymentRepositoryPort)
}
