package com.loopers.coupon.domain

import com.loopers.support.error.ErrorCode

enum class CouponErrorCode(
    override val message: String,
) : ErrorCode {
    COUPON_NOT_FOUND("존재하지 않는 쿠폰입니다."),
    ALREADY_GRANTED("이미 지급된 쿠폰입니다."),
    INVALID_DISCOUNT_VALUE("할인 값은 0보다 커야 합니다."),
    RATE_DISCOUNT_OUT_OF_RANGE("정률 할인은 1~100 사이여야 합니다."),
    EXPIRED_AT_IN_PAST("만료일은 현재 이후여야 합니다."),
    MIN_ORDER_NOT_MET("최소 주문 금액을 충족하지 않습니다."),
    EXPIRED("만료된 쿠폰입니다."),
    ALREADY_USED("이미 사용된 쿠폰입니다."),
    DISCOUNT_NOT_MATCHED("요청된 할인 금액과 실제 할인 금액이 일치하지 않습니다."),
    INVALID_TOTAL_QUANTITY("쿠폰 발급 수량은 0보다 커야 합니다."),
    NOT_ISSUABLE("선착순 발급 대상 쿠폰이 아닙니다."),
    SOLD_OUT("선착순 쿠폰이 모두 소진되었습니다."),
    ALREADY_ISSUED("이미 발급받은 쿠폰입니다."),
    ;

    override val code: String
        get() = "COUPON:$name"
}
