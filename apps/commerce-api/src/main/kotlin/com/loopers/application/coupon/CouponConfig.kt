package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponTemplateRepositoryPort
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.UserCouponRepositoryPort
import com.loopers.domain.coupon.UserCouponService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CouponConfig {
    @Bean
    fun couponTemplateService(couponTemplateRepositoryPort: CouponTemplateRepositoryPort): CouponTemplateService =
        CouponTemplateService(couponTemplateRepositoryPort)

    @Bean
    fun userCouponService(
        userCouponRepositoryPort: UserCouponRepositoryPort,
        couponTemplateRepositoryPort: CouponTemplateRepositoryPort,
    ): UserCouponService = UserCouponService(userCouponRepositoryPort, couponTemplateRepositoryPort)
}
