package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssue

object CouponIssueMapper {
    fun toDomain(entity: CouponIssueEntity): CouponIssue {
        return CouponIssue(
            id = entity.id,
            memberId = entity.memberId,
            couponId = entity.couponId,
            status = entity.status,
            type = entity.type,
            discountValue = entity.discountValue,
            minOrderAmount = entity.minOrderAmount,
            expiredAt = entity.expiredAt,
            usedAt = entity.usedAt,
        )
    }

    fun toEntity(issue: CouponIssue): CouponIssueEntity {
        return CouponIssueEntity(
            memberId = issue.memberId,
            couponId = issue.couponId,
            status = issue.status,
            type = issue.type,
            discountValue = issue.discountValue,
            minOrderAmount = issue.minOrderAmount,
            expiredAt = issue.expiredAt,
            usedAt = issue.usedAt,
        )
    }
}
