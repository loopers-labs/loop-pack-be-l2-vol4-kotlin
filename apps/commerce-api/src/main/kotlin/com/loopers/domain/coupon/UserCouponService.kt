package com.loopers.domain.coupon

import com.loopers.support.function.orThrowNotFound
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class UserCouponService(
    private val userCouponRepository: UserCouponRepository
) {
    fun getByUserId(userId: Long) =
        userCouponRepository.findByUserId(userId)

    fun getWithLockById(id: Long): UserCouponModel =
        userCouponRepository.findWithLockById(id) orThrowNotFound "쿠폰을 찾을 수 없습니다."

    fun getAllByCouponId(couponId: Long, pageable: Pageable) =
        userCouponRepository.findAllByCouponId(couponId, pageable)

    fun issueCoupon(userId: Long, coupon: CouponModel) =
        UserCouponModel.of(coupon, userId)
            .let { userCouponRepository.save(it) }

    fun countIssuedByCouponId(couponId: Long) =
        userCouponRepository.countAllByCouponId(couponId)

    fun hasIssuedTo(couponId: Long, userId: Long) =
        userCouponRepository.existsByCouponIdAndUserId(couponId, userId)
}
