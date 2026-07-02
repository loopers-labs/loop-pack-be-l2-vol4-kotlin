package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestRepositoryPort
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestRepositoryAdapter(
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
) : CouponIssueRequestRepositoryPort {
    override fun save(request: CouponIssueRequest): CouponIssueRequest =
        couponIssueRequestJpaRepository.save(CouponIssueRequestEntity.from(request)).toDomain()

    override fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean =
        couponIssueRequestJpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)

    override fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): CouponIssueRequest? =
        couponIssueRequestJpaRepository.findByUserIdAndCouponTemplateId(userId, couponTemplateId)?.toDomain()
}
