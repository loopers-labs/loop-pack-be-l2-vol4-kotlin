package com.loopers.domain.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface UserCouponRepository {
    fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel>

    fun findByUserId(userId: Long): List<UserCouponModel>

    fun findWithLockById(id: Long): UserCouponModel?

    fun save(userCouponModel: UserCouponModel): UserCouponModel

    fun countAllByCouponId(couponId: Long): Long

    fun existsByCouponIdAndUserId(couponId: Long, userId: Long): Boolean
}
