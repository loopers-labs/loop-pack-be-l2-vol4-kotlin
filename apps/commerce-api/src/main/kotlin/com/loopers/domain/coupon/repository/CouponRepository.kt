package com.loopers.domain.coupon.repository

import com.loopers.domain.coupon.model.Coupon
import org.springframework.data.domain.Page

interface CouponRepository {
    fun findById(couponId: Long): Coupon?

    fun findByIdForUpdate(couponId: Long): Coupon?

    fun findDisplayable(page: Int, size: Int): Page<Coupon>

    fun save(coupon: Coupon): Coupon

    fun update(coupon: Coupon): Coupon

    fun existsByName(name: String): Boolean

    fun existsByNameAndIdNot(name: String, couponId: Long): Boolean
}
