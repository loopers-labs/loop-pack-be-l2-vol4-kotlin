package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCouponModel
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface UserCouponJpaRepository : JpaRepository<UserCouponModel, Long> {
    fun findByIdAndUserId(id: Long, userId: Long): UserCouponModel?
    fun findAllByUserId(userId: Long): List<UserCouponModel>
    fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel>
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean
}
