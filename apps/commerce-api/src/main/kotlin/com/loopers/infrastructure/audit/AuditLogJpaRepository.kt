package com.loopers.infrastructure.audit

import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogJpaRepository : JpaRepository<AuditLogEntity, Long>
