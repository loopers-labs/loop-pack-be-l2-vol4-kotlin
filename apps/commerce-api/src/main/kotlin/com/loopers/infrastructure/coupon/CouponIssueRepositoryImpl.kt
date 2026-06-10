package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssue
import com.loopers.domain.coupon.CouponIssueRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class CouponIssueRepositoryImpl(
    private val couponIssueJpaRepository: CouponIssueJpaRepository,
) : CouponIssueRepository {
    override fun save(issue: CouponIssue): CouponIssue {
        return CouponIssueMapper.toEntity(issue)
            .let(couponIssueJpaRepository::save)
            .let(CouponIssueMapper::toDomain)
    }

    override fun findById(issueId: Long): CouponIssue? {
        return couponIssueJpaRepository.findByIdOrNull(issueId)
            ?.let(CouponIssueMapper::toDomain)
    }

    override fun existsByCouponId(couponId: Long): Boolean {
        return couponIssueJpaRepository.existsByCouponId(couponId)
    }
}
