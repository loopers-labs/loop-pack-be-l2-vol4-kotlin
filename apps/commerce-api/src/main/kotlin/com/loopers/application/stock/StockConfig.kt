package com.loopers.application.stock

import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.domain.stock.StockService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class StockConfig {
    @Bean
    fun stockService(stockRepositoryPort: StockRepositoryPort): StockService = StockService(stockRepositoryPort)
}
