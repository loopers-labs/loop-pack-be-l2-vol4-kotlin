package com.loopers.application.coupon

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponService(
    private val couponRepository: CouponRepository,
) {
    @Transactional(readOnly = true)
    fun getCoupon(couponId: Long): Coupon {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Coupon not found.")

        if (coupon.isDeleted) {
            throw CoreException(ErrorType.NOT_FOUND, "Coupon not found.")
        }

        return coupon
    }

    @Transactional(readOnly = true)
    fun getCoupons(page: Int, size: Int): Page<Coupon> {
        return couponRepository.findDisplayable(page = page, size = size)
    }

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
