package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueRepository
import org.springframework.stereotype.Component

@Component
class CouponIssueRepositoryImpl(
    private val couponIssueJpaRepository: CouponIssueJpaRepository,
) : CouponIssueRepository {
    override fun existsByCouponId(couponId: Long): Boolean {
        return couponIssueJpaRepository.existsByCouponId(couponId)
    }
}
