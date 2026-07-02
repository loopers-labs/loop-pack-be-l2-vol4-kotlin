package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class UserCouponRepositoryImpl(
    private val userCouponJpaRepository: UserCouponJpaRepository
) : UserCouponRepository {
    override fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponModel> =
        userCouponJpaRepository.findAllByCoupon_Id(couponId, pageable)

    override fun findByUserId(userId: Long): List<UserCouponModel> =
        userCouponJpaRepository.findByUserId(userId)

    override fun findWithLockById(id: Long): UserCouponModel? =
        userCouponJpaRepository.findWithLockById(id)

    override fun save(userCouponModel: UserCouponModel): UserCouponModel =
        userCouponJpaRepository.save(userCouponModel)

    override fun countAllByCouponId(couponId: Long): Long =
        userCouponJpaRepository.countAllByCoupon_Id(couponId)

    override fun existsByCouponIdAndUserId(couponId: Long, userId: Long): Boolean =
        userCouponJpaRepository.existsByCoupon_IdAndUserId(couponId, userId)
}
