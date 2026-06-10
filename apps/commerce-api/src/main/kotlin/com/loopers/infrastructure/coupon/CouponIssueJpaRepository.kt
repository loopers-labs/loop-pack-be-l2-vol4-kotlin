package com.loopers.infrastructure.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueJpaRepository : JpaRepository<CouponIssueEntity, Long> {
    fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<CouponIssueEntity>

    fun existsByCouponId(couponId: Long): Boolean
}
