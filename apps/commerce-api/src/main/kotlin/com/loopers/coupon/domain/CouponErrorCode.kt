package com.loopers.coupon.domain

import com.loopers.support.error.ErrorCode

enum class CouponErrorCode(
    override val message: String,
) : ErrorCode {
    INVALID_DISCOUNT_VALUE("할인 값은 0보다 커야 합니다."),
    RATE_DISCOUNT_OUT_OF_RANGE("정률 할인은 1~100 사이여야 합니다."),
    EXPIRED_AT_IN_PAST("만료일은 현재 이후여야 합니다."),
    MIN_ORDER_NOT_MET("최소 주문 금액을 충족하지 않습니다."),
    EXPIRED("만료된 쿠폰입니다."),
    ;

    override val code: String
        get() = "COUPON:$name"
}
