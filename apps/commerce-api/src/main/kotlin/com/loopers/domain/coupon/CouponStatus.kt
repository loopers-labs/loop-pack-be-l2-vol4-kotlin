package com.loopers.domain.coupon

enum class CouponStatus {
    /** 사용 가능 */
    AVAILABLE,

    /** 사용 완료 (재사용 불가) */
    USED,

    /** 만료됨 */
    EXPIRED,
}
