package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponTemplateRepositoryPort
import com.loopers.domain.coupon.CouponTemplateService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CouponConfig {
    @Bean
    fun couponTemplateService(couponTemplateRepositoryPort: CouponTemplateRepositoryPort): CouponTemplateService =
        CouponTemplateService(couponTemplateRepositoryPort)
}
