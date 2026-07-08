package com.loopers.domain.queue

import java.time.ZonedDateTime

interface WaitingQueueRepository {
    fun enter(userId: Long, enteredAt: ZonedDateTime)

    fun findRank(userId: Long): Long?

    fun size(): Long

    fun pop(count: Long): List<Long>
}
