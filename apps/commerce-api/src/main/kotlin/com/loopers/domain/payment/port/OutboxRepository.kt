package com.loopers.domain.payment.port

import com.loopers.domain.payment.model.OutboxEventModel

interface OutboxRepository {
    fun save(event: OutboxEventModel): OutboxEventModel
}
