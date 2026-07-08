package com.loopers.infrastructure.audit

import com.loopers.domain.audit.AuditLog
import com.loopers.domain.audit.AuditLogRepositoryPort
import org.springframework.stereotype.Component

@Component
class AuditLogRepositoryAdapter(
    private val auditLogJpaRepository: AuditLogJpaRepository,
) : AuditLogRepositoryPort {
    override fun save(auditLog: AuditLog): AuditLog =
        auditLogJpaRepository.save(AuditLogEntity.from(auditLog)).toDomain()
}
