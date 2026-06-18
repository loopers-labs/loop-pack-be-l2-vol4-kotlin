package com.loopers.application.coupon.usecase.admin

import com.loopers.application.coupon.AdminCouponInfo
import com.loopers.application.coupon.UpsertCouponCommand
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreateCouponUsecase(
    private val couponRepository: CouponRepository,
) {
    @Transactional
    fun execute(command: UpsertCouponCommand): AdminCouponInfo {
        val coupon = CouponModel(
            name = command.name,
            type = command.type,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
        )
        return AdminCouponInfo.from(couponRepository.save(coupon))
    }
}
