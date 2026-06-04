package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponType

/**
 * [CouponType] 의 영속 표현. 도메인 enum 을 인프라에 그대로 노출하지 않고 명시적으로 변환한다.
 */
enum class PersistedCouponType {
    FIXED,
    RATE,
    ;

    fun toDomain(): CouponType = when (this) {
        FIXED -> CouponType.FIXED
        RATE -> CouponType.RATE
    }

    companion object {
        fun from(domain: CouponType): PersistedCouponType = when (domain) {
            CouponType.FIXED -> FIXED
            CouponType.RATE -> RATE
        }
    }
}
