package com.loopers.application.audit

import com.loopers.domain.audit.AuditLogRepositoryPort
import com.loopers.domain.audit.AuditService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AuditConfig {
    @Bean
    fun auditService(auditLogRepositoryPort: AuditLogRepositoryPort): AuditService =
        AuditService(auditLogRepositoryPort)
}
