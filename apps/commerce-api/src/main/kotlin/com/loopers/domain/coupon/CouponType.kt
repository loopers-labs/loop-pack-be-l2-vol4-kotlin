package com.loopers.domain.coupon

enum class CouponType {
    /** 정액 할인: value 만큼(원) 차감 */
    FIXED,

    /** 정률 할인: value 퍼센트(%) 차감 */
    RATE,
}
