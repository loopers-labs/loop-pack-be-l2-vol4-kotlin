package com.loopers.domain.audit

interface AuditLogRepositoryPort {
    fun save(auditLog: AuditLog): AuditLog
}
