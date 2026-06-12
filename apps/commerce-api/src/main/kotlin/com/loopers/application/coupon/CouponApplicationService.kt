package com.loopers.application.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional(readOnly = true)
class CouponApplicationService(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    fun getUsableCoupon(userId: Long, userCouponId: Long): Coupon {
        val userCoupon = userCouponRepository.findById(userCouponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "발급 쿠폰을 찾을 수 없습니다. id=$userCouponId")

        userCoupon.validateOwnedBy(userId)
        userCoupon.validateUsable()

        return couponRepository.findById(userCoupon.couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰 정보를 찾을 수 없습니다. id=${userCoupon.couponId}")
    }

    @Transactional
    fun renameCoupon(id: Long, name: String): Coupon {
        val coupon = couponRepository.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다. id=$id")

        coupon.rename(name)
        return couponRepository.save(coupon)
    }

    @Transactional
    fun useCoupon(userId: Long, userCouponId: Long) {
        val userCoupon = userCouponRepository.findById(userCouponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "발급 쿠폰을 찾을 수 없습니다. id=$userCouponId")

        userCoupon.validateOwnedBy(userId)

        if (!userCouponRepository.useIfNotUsed(id = userCouponId, userId = userId, usedAt = LocalDateTime.now())) {
            throw CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다. id=$userCouponId")
        }
    }

    @Transactional
    fun cancelCouponUse(userId: Long, userCouponId: Long) {
        val userCoupon = userCouponRepository.findById(userCouponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "발급 쿠폰을 찾을 수 없습니다. id=$userCouponId")

        userCoupon.validateOwnedBy(userId)

        if (!userCouponRepository.cancelUseIfUsed(id = userCouponId, userId = userId)) {
            throw CoreException(ErrorType.CONFLICT, "사용되지 않은 쿠폰입니다. id=$userCouponId")
        }
    }
}
