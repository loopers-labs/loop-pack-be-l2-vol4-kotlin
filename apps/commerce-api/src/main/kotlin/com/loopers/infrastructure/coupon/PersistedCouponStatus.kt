package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponStatus

/**
 * [CouponStatus] 의 영속 표현. 도메인 enum 을 인프라에 그대로 노출하지 않고 명시적으로 변환한다.
 */
enum class PersistedCouponStatus {
    AVAILABLE,
    USED,
    EXPIRED,
    ;

    fun toDomain(): CouponStatus = when (this) {
        AVAILABLE -> CouponStatus.AVAILABLE
        USED -> CouponStatus.USED
        EXPIRED -> CouponStatus.EXPIRED
    }

    companion object {
        fun from(domain: CouponStatus): PersistedCouponStatus = when (domain) {
            CouponStatus.AVAILABLE -> AVAILABLE
            CouponStatus.USED -> USED
            CouponStatus.EXPIRED -> EXPIRED
        }
    }
}
