package com.loopers.domain.coupon.port

import java.util.UUID

interface CouponIssueEventHandledRepository {
    fun recordIfAbsent(
        eventId: UUID,
        consumerGroup: String,
        eventType: String,
    ): Boolean

    fun exists(eventId: UUID, consumerGroup: String): Boolean
}
