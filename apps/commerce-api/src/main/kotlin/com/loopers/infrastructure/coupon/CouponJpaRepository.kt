package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CouponJpaRepository : JpaRepository<Coupon, Long> {
    fun findByIdAndDeletedAtIsNull(couponId: Long): Coupon?

    fun findAllByDeletedAtIsNullOrderByIdDesc(pageable: Pageable): List<Coupon>
}
