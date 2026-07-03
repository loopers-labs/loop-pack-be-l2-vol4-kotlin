package com.loopers.application.event

import java.time.ZonedDateTime

data class CouponIssueRequestedEvent(
    override val userId: Long,
    val couponId: Long,
    val requestId: String,
    override val occurredAt: String = ZonedDateTime.now().toString(),
) : UserActivityEvent, IntegrationEvent {
    override val activityType: String = EVENT_TYPE
    override val eventType: String = EVENT_TYPE
    override val description: String = "쿠폰 발급 요청: couponId=$couponId, requestId=$requestId"
    override val aggregateType: String = EventAggregateType.COUPON.value
    override val aggregateId: String = couponId.toString()
    override val topic: String = EventTopic.COUPON_ISSUE_REQUESTS.value

    companion object {
        const val EVENT_TYPE = "COUPON_ISSUE_REQUESTED"
    }
}
