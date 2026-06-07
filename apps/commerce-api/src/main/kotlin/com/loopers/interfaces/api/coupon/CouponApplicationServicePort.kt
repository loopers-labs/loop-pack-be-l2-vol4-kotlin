package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.MyCouponResult
import com.loopers.application.coupon.UserCouponResult

/**
 * 대고객 쿠폰 인바운드 포트. 관리자용은 [CouponAdminApplicationServicePort] 에 분리되어 있으며,
 * 두 포트 모두 하나의 CouponApplicationServiceAdapter 가 구현한다.
 */
interface CouponApplicationServicePort {
    fun issueCoupon(userId: Long, couponId: Long): UserCouponResult

    /** 사용자가 발급받은 쿠폰 목록을 상태(조회 시점 만료 반영)와 함께 반환한다. */
    fun getMyCoupons(userId: Long): List<MyCouponResult>
}
