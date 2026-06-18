package com.loopers.infrastructure.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface UserCouponJpaRepository : JpaRepository<UserCouponEntity, Long> {
    fun existsByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): Boolean
    fun findAllByUserIdOrderByIdDesc(userId: Long): List<UserCouponEntity>
    fun findAllByCouponTemplateId(couponTemplateId: Long, pageable: Pageable): Page<UserCouponEntity>
}
