package com.loopers.outbox.domain

interface OutboxEventRepository {
    fun save(outboxEvent: OutboxEvent): OutboxEvent
}
