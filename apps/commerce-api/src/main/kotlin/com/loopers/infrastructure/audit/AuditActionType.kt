package com.loopers.infrastructure.audit

import com.loopers.domain.audit.AuditAction

enum class AuditActionType {
    LIKE,
    UNLIKE,
    ORDER_CREATED,
    PAYMENT_COMPLETED,
    ;

    fun toDomain(): AuditAction = when (this) {
        LIKE -> AuditAction.LIKE
        UNLIKE -> AuditAction.UNLIKE
        ORDER_CREATED -> AuditAction.ORDER_CREATED
        PAYMENT_COMPLETED -> AuditAction.PAYMENT_COMPLETED
    }

    companion object {
        fun from(domain: AuditAction): AuditActionType = when (domain) {
            AuditAction.LIKE -> LIKE
            AuditAction.UNLIKE -> UNLIKE
            AuditAction.ORDER_CREATED -> ORDER_CREATED
            AuditAction.PAYMENT_COMPLETED -> PAYMENT_COMPLETED
        }
    }
}
