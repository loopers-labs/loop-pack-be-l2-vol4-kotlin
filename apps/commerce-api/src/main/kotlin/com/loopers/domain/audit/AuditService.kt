package com.loopers.domain.audit

import java.time.ZonedDateTime

class AuditService(
    private val auditLogRepositoryPort: AuditLogRepositoryPort,
) {
    fun record(
        action: AuditAction,
        actorId: Long,
        targetId: Long,
        description: String,
    ): AuditLog =
        auditLogRepositoryPort.save(
            AuditLog(
                action = action,
                actorId = actorId,
                targetId = targetId,
                description = description,
                occurredAt = ZonedDateTime.now(),
            ),
        )
}
