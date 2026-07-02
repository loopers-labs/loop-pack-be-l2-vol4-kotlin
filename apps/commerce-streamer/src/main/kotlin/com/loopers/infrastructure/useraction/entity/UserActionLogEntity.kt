package com.loopers.infrastructure.useraction.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.useraction.UserActionLog
import com.loopers.domain.useraction.UserActionType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(
    name = "user_action_log",
    indexes = [
        Index(name = "uk_user_action_log_event_id", columnList = "event_id", unique = true),
        Index(name = "idx_user_action_log_member_id", columnList = "member_id"),
    ],
)
class UserActionLogEntity(
    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    var eventId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 100)
    var actionType: UserActionType,

    @Column(name = "member_id")
    var memberId: Long?,

    @Column(name = "aggregate_id", nullable = false)
    var aggregateId: Long,

    @Column(name = "product_id")
    var productId: Long?,

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: ZonedDateTime,
) : BaseEntity() {
    fun toDomain(): UserActionLog {
        return UserActionLog(
            eventId = eventId,
            actionType = actionType,
            memberId = memberId,
            aggregateId = aggregateId,
            productId = productId,
            occurredAt = occurredAt,
        )
    }
}
