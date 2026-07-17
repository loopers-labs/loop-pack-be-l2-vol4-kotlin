package com.loopers.config.kafka.event

import java.time.Instant

/**
 * 선착순 쿠폰 발급 요청 메시지.
 * coupon-issue-requests 토픽으로 발행되며, Consumer가 순차적으로 발급을 처리한다.
 *
 * @property requestId coupon_issue_requests 테이블의 PK (결과 조회 키)
 * @property couponId 발급 대상 쿠폰 템플릿 ID (파티션 키로도 사용)
 * @property userId 발급 요청 사용자 ID
 * @property occurredAt 요청 발생 시각
 */
data class CouponIssueRequestMessage(
    val requestId: Long,
    val couponId: Long,
    val userId: Long,
    val occurredAt: Instant = Instant.now(),
)
