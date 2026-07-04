package com.loopers.domain.coupon.presentation

import java.util.UUID

data class CouponIssueRequestKafkaEvent(
    val eventId: UUID,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: Long,
    val payload: String,
)
