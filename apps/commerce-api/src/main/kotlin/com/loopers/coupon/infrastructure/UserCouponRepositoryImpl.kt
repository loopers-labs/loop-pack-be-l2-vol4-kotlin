package com.loopers.coupon.infrastructure

import com.loopers.coupon.domain.UserCoupon
import com.loopers.coupon.domain.UserCouponRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
class UserCouponRepositoryImpl(
    val userCouponJpaRepository: UserCouponJpaRepository,
) : UserCouponRepository {
    override fun save(userCoupon: UserCoupon): UserCoupon = userCouponJpaRepository.save(userCoupon)

    override fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean =
        userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId)

    override fun findByUserIdAndCouponId(userId: Long, couponId: Long): UserCoupon? =
        userCouponJpaRepository.findByUserIdAndCouponId(userId, couponId)
}

interface UserCouponJpaRepository : JpaRepository<UserCoupon, Long> {
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    fun findByUserIdAndCouponId(userId: Long, couponId: Long): UserCoupon?

    fun countByCouponId(couponId: Long): Long
}
