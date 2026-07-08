package com.loopers.domain.coupon

/**
 * 선착순 쿠폰 발급 요청 메시지 (coupon-issue-requests 토픽).
 * Producer/Consumer 가 같은 앱(commerce-api)이라 클래스를 공유한다.
 */
data class CouponIssueMessage(
    val requestId: String,
    val userId: Long,
    val couponId: Long,
)
