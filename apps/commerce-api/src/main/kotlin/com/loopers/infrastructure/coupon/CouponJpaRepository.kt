package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository

interface CouponJpaRepository : JpaRepository<CouponJpaEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CouponJpaEntity?
}
