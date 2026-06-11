package com.loopers.domain.coupon.vo

import com.loopers.domain.coupon.exception.InvalidCouponException

enum class CouponType {
    FIXED_AMOUNT,
    PERCENTAGE,
    ;

    companion object {
        fun fromApiType(type: String): CouponType =
            when (type) {
                "FIXED" -> FIXED_AMOUNT
                "RATE" -> PERCENTAGE
                else -> throw InvalidCouponException("지원하지 않는 쿠폰 타입입니다. type=$type")
            }
    }
}
