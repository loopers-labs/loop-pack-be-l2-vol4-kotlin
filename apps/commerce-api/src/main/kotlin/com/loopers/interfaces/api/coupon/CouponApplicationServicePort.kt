package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponIssueRequestResult
import com.loopers.application.coupon.MyCouponResult

/**
 * 대고객 쿠폰 인바운드 포트. 관리자용은 [CouponAdminApplicationServicePort] 에 분리되어 있으며,
 * 두 포트 모두 하나의 CouponApplicationServiceAdapter 가 구현한다.
 */
interface CouponApplicationServicePort {
    /** 선착순 쿠폰 발급 요청. Redis 수량 차감 후 PENDING 상태의 발급 요청을 생성하고 Kafka로 비동기 처리한다. */
    fun issueCoupon(userId: Long, couponId: Long): CouponIssueRequestResult

    /** 발급 요청 상태 조회(폴링용). PENDING/COMPLETED/FAILED 를 반환한다. */
    fun getIssueStatus(userId: Long, couponId: Long): CouponIssueRequestResult

    /** 사용자가 발급받은 쿠폰 목록을 상태(조회 시점 만료 반영)와 함께 반환한다. */
    fun getMyCoupons(userId: Long): List<MyCouponResult>
}
