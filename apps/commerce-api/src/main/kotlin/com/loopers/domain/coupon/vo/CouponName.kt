package com.loopers.domain.coupon.vo

import com.loopers.domain.coupon.exception.InvalidCouponException

@JvmInline
value class CouponName private constructor(
    val value: String,
) {
    companion object {
        fun of(value: String): CouponName {
            validate(value)
            return CouponName(value)
        }

        private fun validate(value: String) {
            if (value.isBlank()) {
                throw InvalidCouponException("쿠폰명은 필수입니다.")
            }
        }
    }
}
