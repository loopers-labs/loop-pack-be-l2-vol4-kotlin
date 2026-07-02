package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueRequestJpaRepository : JpaRepository<CouponIssueRequestEntity, Long> {
    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean
    fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): CouponIssueRequestEntity?
}
