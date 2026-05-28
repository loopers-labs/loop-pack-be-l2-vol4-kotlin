package com.loopers.application.brand

import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.brand.BrandService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BrandConfig {
    @Bean
    fun brandService(brandRepositoryPort: BrandRepositoryPort): BrandService =
        BrandService(brandRepositoryPort)
}
