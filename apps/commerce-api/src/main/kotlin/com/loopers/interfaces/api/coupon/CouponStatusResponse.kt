package com.loopers.interfaces.api.coupon

import com.loopers.domain.coupon.CouponStatus

/**
 * [CouponStatus] 의 API 응답 표현. 도메인 enum 을 인터페이스에 그대로 노출하지 않고 명시적으로 변환한다.
 * (인프라의 PersistedCouponStatus 와 대칭되는, 인터페이스 레이어의 독립 표현)
 */
enum class CouponStatusResponse {
    AVAILABLE,
    USED,
    EXPIRED,
    ;

    companion object {
        fun from(domain: CouponStatus): CouponStatusResponse = when (domain) {
            CouponStatus.AVAILABLE -> AVAILABLE
            CouponStatus.USED -> USED
            CouponStatus.EXPIRED -> EXPIRED
        }
    }
}
