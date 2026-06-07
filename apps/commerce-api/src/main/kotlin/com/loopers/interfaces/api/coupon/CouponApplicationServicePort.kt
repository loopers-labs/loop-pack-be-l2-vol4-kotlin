package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.UserCouponResult

/**
 * 대고객 쿠폰 인바운드 포트. 관리자용은 [CouponAdminApplicationServicePort] 에 분리되어 있으며,
 * 두 포트 모두 하나의 CouponApplicationServiceAdapter 가 구현한다.
 */
interface CouponApplicationServicePort {
    fun issueCoupon(userId: Long, couponId: Long): UserCouponResult
}
