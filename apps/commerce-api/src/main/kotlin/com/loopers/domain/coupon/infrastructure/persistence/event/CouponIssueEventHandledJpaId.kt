package com.loopers.domain.coupon.infrastructure.persistence.event

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.util.UUID

@Embeddable
data class CouponIssueEventHandledJpaId(
    @Column(name = "event_id", nullable = false, columnDefinition = "BINARY(16)")
    var eventId: UUID = UUID(0L, 0L),
    @Column(name = "consumer_group", nullable = false, length = 128)
    var consumerGroup: String = "",
) : Serializable
