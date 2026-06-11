package com.loopers.application.order

import com.loopers.domain.order.OrderRepositoryPort
import com.loopers.domain.order.OrderService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OrderConfig {
    @Bean
    fun orderService(orderRepositoryPort: OrderRepositoryPort): OrderService = OrderService(orderRepositoryPort)
}
