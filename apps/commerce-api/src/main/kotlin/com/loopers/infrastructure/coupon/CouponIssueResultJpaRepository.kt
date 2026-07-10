package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueStatus
import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueResultJpaRepository : JpaRepository<CouponIssueResultJpaEntity, Long> {
    fun findByRequestId(requestId: String): CouponIssueResultJpaEntity?

    fun existsByUserIdAndCouponIdAndStatus(userId: Long, couponId: Long, status: CouponIssueStatus): Boolean
}
