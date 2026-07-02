package com.loopers.domain.event

import java.time.ZonedDateTime

data class UserActionLogPayload(
    val eventId: String,
    val userId: Long,
    val actionType: UserActionType,
    val targetId: Long,
    val occurredAt: ZonedDateTime,
)

enum class UserActionType { LIKED, UNLIKED, ORDERED }
