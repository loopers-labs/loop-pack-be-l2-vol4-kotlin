package com.loopers.domain.outbox

interface OutboxRepository {
    fun save(outbox: OutboxModel): OutboxModel
    fun findAllByStatus(status: OutboxStatus): List<OutboxModel>
}
