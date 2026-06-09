package com.loopers.application.coupon

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponService(
    private val couponRepository: CouponRepository,
) {
    @Transactional
    fun createCoupon(command: CouponCreateCommand): Coupon {
        if (couponRepository.existsByName(command.name)) {
            throw CoreException(ErrorType.CONFLICT, "Coupon name already exists.")
        }

        return Coupon(
            name = command.name,
            type = command.type,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
        ).let(couponRepository::save)
    }
}
