package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueStatus

enum class PersistedCouponIssueStatus {
    PENDING,
    COMPLETED,
    FAILED,
    ;

    fun toDomain(): CouponIssueStatus = when (this) {
        PENDING -> CouponIssueStatus.PENDING
        COMPLETED -> CouponIssueStatus.COMPLETED
        FAILED -> CouponIssueStatus.FAILED
    }

    companion object {
        fun from(domain: CouponIssueStatus): PersistedCouponIssueStatus = when (domain) {
            CouponIssueStatus.PENDING -> PENDING
            CouponIssueStatus.COMPLETED -> COMPLETED
            CouponIssueStatus.FAILED -> FAILED
        }
    }
}
