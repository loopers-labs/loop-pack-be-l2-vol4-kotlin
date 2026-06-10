package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime

object CouponPolicy {
    fun validate(
        name: String,
        type: DiscountType,
        discountValue: Long,
        minOrderAmount: Long?,
        expiredAt: ZonedDateTime,
    ) {
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Name must not be blank.")
        }
        if (minOrderAmount != null && minOrderAmount < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "MinimumOrderAmount cannot be less than zero.")
        }
        if (!expiredAt.isAfter(ZonedDateTime.now())) {
            throw CoreException(ErrorType.BAD_REQUEST, "Coupon expiration time must be in the future.")
        }

        when (type) {
            DiscountType.FIXED -> {
                if (discountValue <= 0L) {
                    throw CoreException(ErrorType.BAD_REQUEST, "Fixed discount amount must be greater than zero.")
                }
            }
            DiscountType.RATE -> {
                if (discountValue !in 1..100) {
                    throw CoreException(ErrorType.BAD_REQUEST, "Rate discount must be between 1 and 100.")
                }
            }
        }
    }
}
