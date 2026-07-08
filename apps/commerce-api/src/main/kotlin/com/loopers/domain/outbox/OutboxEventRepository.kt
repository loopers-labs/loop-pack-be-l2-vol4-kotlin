package com.loopers.domain.outbox

interface OutboxEventRepository {
    fun save(event: OutboxEventModel): OutboxEventModel
    fun findPendingBatch(limit: Int): List<OutboxEventModel>
    fun markPublished(ids: List<Long>)
}
