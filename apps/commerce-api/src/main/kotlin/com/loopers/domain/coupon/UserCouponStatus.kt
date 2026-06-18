package com.loopers.domain.coupon

/** EXPIRED는 저장되지 않고 조회 시 템플릿 만료 여부로 파생된다. */
enum class UserCouponStatus {
    AVAILABLE,
    USED,
    EXPIRED,
}
