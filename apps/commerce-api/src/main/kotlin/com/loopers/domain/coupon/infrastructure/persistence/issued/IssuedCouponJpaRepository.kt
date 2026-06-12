package com.loopers.domain.coupon.infrastructure.persistence.issued

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface IssuedCouponJpaRepository : JpaRepository<IssuedCouponJpaEntity, Long> {
    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean

    fun findByUserIdOrderByIssuedAtDesc(userId: Long): List<IssuedCouponJpaEntity>

    fun findByCouponTemplateId(
        couponTemplateId: Long,
        pageable: Pageable,
    ): Page<IssuedCouponJpaEntity>
}
