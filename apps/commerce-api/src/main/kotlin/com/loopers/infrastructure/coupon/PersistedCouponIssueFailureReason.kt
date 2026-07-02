package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueFailureReason

enum class PersistedCouponIssueFailureReason {
    DUPLICATE,
    SYSTEM_ERROR,
    ;

    fun toDomain(): CouponIssueFailureReason = when (this) {
        DUPLICATE -> CouponIssueFailureReason.DUPLICATE
        SYSTEM_ERROR -> CouponIssueFailureReason.SYSTEM_ERROR
    }

    companion object {
        fun from(domain: CouponIssueFailureReason): PersistedCouponIssueFailureReason = when (domain) {
            CouponIssueFailureReason.DUPLICATE -> DUPLICATE
            CouponIssueFailureReason.SYSTEM_ERROR -> SYSTEM_ERROR
        }
    }
}
