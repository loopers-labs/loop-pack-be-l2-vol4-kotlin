package com.loopers.application.coupon

import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.withId
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable

class InMemoryUserCouponRepository : UserCouponRepository {
    private val store = mutableMapOf<Long, UserCouponModel>()
    private var sequence = 0L

    override fun save(userCoupon: UserCouponModel): UserCouponModel {
        if (userCoupon.id == 0L && existsByUserIdAndCouponId(userCoupon.userId, userCoupon.couponId)) {
            throw DataIntegrityViolationException("uk_user_coupons_user_coupon violation")
        }
        val saved = if (userCoupon.id == 0L) userCoupon.withId(++sequence) else userCoupon
        store[saved.id] = saved
        return saved
    }

    override fun findByIdAndUserId(id: Long, userId: Long): UserCouponModel? {
        return store[id]?.takeIf { it.userId == userId }
    }

    override fun findAllByUserId(userId: Long): List<UserCouponModel> {
        return store.values.filter { it.userId == userId }.sortedByDescending { it.id }
    }

    override fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel> {
        val matched = store.values.filter { it.couponId == couponId }.sortedByDescending { it.id }
        val content = matched.drop(pageable.pageNumber * pageable.pageSize).take(pageable.pageSize)
        return PageImpl(content, pageable, matched.size.toLong())
    }

    override fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean {
        return store.values.any { it.userId == userId && it.couponId == couponId }
    }
}
