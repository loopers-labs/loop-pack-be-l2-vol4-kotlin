package com.loopers.domain.payment.port

import com.loopers.domain.payment.model.OutboxEventModel
import com.loopers.domain.payment.model.OutboxEventType

interface OutboxRepository {
    fun save(event: OutboxEventModel): OutboxEventModel
    fun findPendingByType(type: OutboxEventType): List<OutboxEventModel>
    fun markProcessed(id: Long)
}
