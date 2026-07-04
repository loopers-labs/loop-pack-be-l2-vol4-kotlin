package com.loopers.domain.coupon.infrastructure.persistence.event

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "event_handled",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_event_handled_consumer_group_event_id",
            columnNames = ["consumer_group", "event_id"],
        ),
    ],
)
class CouponIssueEventHandledJpaEntity(
    @EmbeddedId
    var id: CouponIssueEventHandledJpaId,
    @Column(name = "event_type", nullable = false, length = 100)
    var eventType: String,
    @Column(name = "processed_at", nullable = false)
    var processedAt: Instant = Instant.now(),
)
