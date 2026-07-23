package com.loopers.infrastructure.coupon.repository

import com.loopers.domain.coupon.model.CouponIssue
import com.loopers.domain.coupon.repository.CouponIssueRepository
import com.loopers.infrastructure.coupon.entity.CouponIssueEntity
import org.springframework.stereotype.Component

@Component
class CouponIssueRepositoryImpl(
    private val couponIssueJpaRepository: CouponIssueJpaRepository,
) : CouponIssueRepository {
    override fun save(issue: CouponIssue): CouponIssue {
        return CouponIssueEntity(
            memberId = issue.memberId,
            couponId = issue.couponId,
            status = issue.status,
            type = issue.type,
            discountValue = issue.discountValue,
            minOrderAmount = issue.minOrderAmount,
            expiredAt = issue.expiredAt,
            usedAt = issue.usedAt,
        ).let(couponIssueJpaRepository::save)
            .let {
                CouponIssue(
                    id = it.id,
                    memberId = it.memberId,
                    couponId = it.couponId,
                    status = it.status,
                    type = it.type,
                    discountValue = it.discountValue,
                    minOrderAmount = it.minOrderAmount,
                    expiredAt = it.expiredAt,
                    usedAt = it.usedAt,
                )
            }
    }

    override fun existsByCouponIdAndMemberId(couponId: Long, memberId: Long): Boolean {
        return couponIssueJpaRepository.existsByCouponIdAndMemberId(couponId = couponId, memberId = memberId)
    }
}
