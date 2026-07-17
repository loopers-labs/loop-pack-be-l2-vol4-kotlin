package com.loopers.outbox.domain

import java.time.ZonedDateTime

interface OutboxEventRepository {
    fun save(outboxEvent: OutboxEvent): OutboxEvent

    fun findByStatus(status: OutboxStatus, limit: Int): List<OutboxEvent>

    fun markSent(ids: List<Long>): Int

    fun registerFailure(ids: List<Long>, maxRetry: Int): List<Long>

    fun deleteSentBefore(threshold: ZonedDateTime): Int
}
