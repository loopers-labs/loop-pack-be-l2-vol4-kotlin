package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponTemplateRepositoryPort
import org.springframework.stereotype.Component

@Component
class CouponTemplateRepositoryAdapter(
    private val couponTemplateJpaRepository: CouponTemplateJpaRepository,
) : CouponTemplateRepositoryPort {
    override fun save(couponTemplate: CouponTemplate): CouponTemplate =
        couponTemplateJpaRepository.save(CouponTemplateEntity.from(couponTemplate)).toDomain()

    override fun findById(id: Long): CouponTemplate? =
        couponTemplateJpaRepository.findById(id).map { it.toDomain() }.orElse(null)
}
