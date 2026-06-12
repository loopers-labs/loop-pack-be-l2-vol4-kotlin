package com.loopers.application.coupon.usecase.admin

import com.loopers.application.coupon.AdminCouponInfo
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetCouponUsecase(
    private val couponRepository: CouponRepository,
) {
    @Transactional(readOnly = true)
    fun execute(couponId: Long): AdminCouponInfo {
        val coupon = couponRepository.findActiveById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        return AdminCouponInfo.from(coupon)
    }
}
