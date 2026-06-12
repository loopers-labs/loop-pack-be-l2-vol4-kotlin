package com.loopers.application.coupon.usecase.admin

import com.loopers.application.coupon.AdminCouponInfo
import com.loopers.application.coupon.UpsertCouponCommand
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UpdateCouponUsecase(
    private val couponRepository: CouponRepository,
) {
    @Transactional
    fun execute(couponId: Long, command: UpsertCouponCommand): AdminCouponInfo {
        val coupon = couponRepository.findActiveById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        coupon.update(
            name = command.name,
            type = command.type,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
        )
        return AdminCouponInfo.from(coupon)
    }
}
