package com.loopers.application.coupon.dto

import com.loopers.domain.coupon.enums.CouponIssueDisplayStatus
import com.loopers.domain.coupon.enums.DiscountType
import com.loopers.domain.coupon.model.CouponIssue
import java.time.ZonedDateTime

data class CouponIssueInfo(
    val issueId: Long,
    val couponId: Long,
    val memberId: Long,
    val status: CouponIssueDisplayStatus,
    val type: DiscountType,
    val value: Long,
    val minOrderAmount: Long?,
    val expiredAt: ZonedDateTime,
    val usedAt: ZonedDateTime?,
) {
    companion object {
        fun from(issue: CouponIssue): CouponIssueInfo {
            return CouponIssueInfo(
                issueId = issue.id,
                couponId = issue.couponId,
                memberId = issue.memberId,
                status = issue.displayStatusAt(ZonedDateTime.now()),
                type = issue.type,
                value = issue.discountValue,
                minOrderAmount = issue.minOrderAmount,
                expiredAt = issue.expiredAt,
                usedAt = issue.usedAt,
            )
        }
    }
}
