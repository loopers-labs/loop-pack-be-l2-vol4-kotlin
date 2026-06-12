package com.loopers.application.coupon.usecase

import com.loopers.application.coupon.MyCouponInfo
import com.loopers.application.coupon.MyCouponsCommand
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class GetMyCouponsUsecase(
    private val userService: UserService,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    @Transactional(readOnly = true)
    fun execute(command: MyCouponsCommand): List<MyCouponInfo> {
        val now = ZonedDateTime.now()
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        val userCoupons = userCouponRepository.findAllByUserId(user.id)
        val couponsById = couponRepository.findAllByIdIn(userCoupons.map { it.couponId }.distinct())
            .associateBy { it.id }

        return userCoupons.map { userCoupon ->
            val coupon = couponsById[userCoupon.couponId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다.")
            MyCouponInfo.from(userCoupon = userCoupon, coupon = coupon, now = now)
        }
    }
}
