package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueJpaRepository : JpaRepository<CouponIssueEntity, Long> {
    fun existsByCouponId(couponId: Long): Boolean
}
