package com.loopers.interfaces.consumer.message

import java.time.ZonedDateTime

data class UserActionLoggedMessage(
    val eventId: String,
    val userId: Long,
    val actionType: String,
    val targetId: Long,
    val occurredAt: ZonedDateTime,
)
