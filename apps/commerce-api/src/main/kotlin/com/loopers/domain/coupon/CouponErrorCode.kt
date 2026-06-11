package com.loopers.domain.coupon

import com.loopers.support.error.ErrorCode

enum class CouponErrorCode(
    override val message: String,
) : ErrorCode {
    MIN_ORDER_NOT_MET("최소 주문 금액을 충족하지 않습니다."),
    EXPIRED("만료된 쿠폰입니다."),
    ;

    override val code: String
        get() = "COUPON:$name"
}
