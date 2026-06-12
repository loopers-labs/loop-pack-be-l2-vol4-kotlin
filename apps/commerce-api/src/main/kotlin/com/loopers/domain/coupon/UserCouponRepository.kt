package com.loopers.domain.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UserCouponRepository {
    fun save(userCoupon: UserCouponModel): UserCouponModel
    fun findByIdAndUserId(id: Long, userId: Long): UserCouponModel?
    fun findAllByUserId(userId: Long): List<UserCouponModel>
    fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel>
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean
}
