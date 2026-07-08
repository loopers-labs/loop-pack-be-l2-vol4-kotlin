package com.loopers.domain.audit

import java.time.ZonedDateTime

class AuditLog(
    val id: Long = 0L,
    val action: AuditAction,
    val actorId: Long,
    val targetId: Long,
    val description: String,
    val occurredAt: ZonedDateTime,
)
