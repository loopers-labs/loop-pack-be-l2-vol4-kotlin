package com.loopers.infrastructure.coupon.repository

import com.loopers.infrastructure.coupon.entity.CouponIssueEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueJpaRepository : JpaRepository<CouponIssueEntity, Long> {
    fun existsByCouponIdAndMemberId(couponId: Long, memberId: Long): Boolean

    fun countByCouponId(couponId: Long): Long
}
