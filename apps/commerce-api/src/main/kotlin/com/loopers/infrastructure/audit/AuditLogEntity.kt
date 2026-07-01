package com.loopers.infrastructure.audit

import com.loopers.domain.audit.AuditLog
import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "audit_logs",
    indexes = [
        Index(name = "idx_audit_logs_actor_id", columnList = "actor_id"),
        Index(name = "idx_audit_logs_target_id", columnList = "target_id"),
    ],
)
class AuditLogEntity(
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    val action: AuditActionType,

    @Column(name = "actor_id", nullable = false)
    val actorId: Long,

    @Column(name = "target_id", nullable = false)
    val targetId: Long,

    @Column(name = "description", nullable = false, length = 255)
    val description: String,
) : BaseEntity() {

    fun toDomain(): AuditLog = AuditLog(
        id = id,
        action = action.toDomain(),
        actorId = actorId,
        targetId = targetId,
        description = description,
        occurredAt = createdAt,
    )

    companion object {
        fun from(auditLog: AuditLog): AuditLogEntity = AuditLogEntity(
            action = AuditActionType.from(auditLog.action),
            actorId = auditLog.actorId,
            targetId = auditLog.targetId,
            description = auditLog.description,
        )
    }
}
