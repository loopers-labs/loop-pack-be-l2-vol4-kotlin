package com.loopers.application.coupon.usecase

import com.loopers.application.coupon.IssueCouponCommand
import com.loopers.application.coupon.MyCouponInfo
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class IssueCouponUsecase(
    private val userService: UserService,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    @Transactional
    fun execute(command: IssueCouponCommand): MyCouponInfo {
        val now = ZonedDateTime.now()
        val user = userService.getProfile(loginId = command.loginId, password = command.password)
        val coupon = couponRepository.findActiveById(command.couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        if (coupon.isExpired(now)) throw CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급받을 수 없습니다.")
        if (userCouponRepository.existsByUserIdAndCouponId(userId = user.id, couponId = coupon.id)) {
            throw CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.")
        }

        val userCoupon = try {
            userCouponRepository.save(UserCouponModel(userId = user.id, couponId = coupon.id))
        } catch (e: DataIntegrityViolationException) {
            // 사전 중복 체크를 통과한 동시 요청이 유니크 제약에 걸린 경우
            throw CoreException(ErrorType.CONFLICT, "이미 발급받은 쿠폰입니다.")
        }
        return MyCouponInfo.from(userCoupon = userCoupon, coupon = coupon, now = now)
    }
}
