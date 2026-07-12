package com.loopers.domain.waitingqueue

interface WaitingQueueRepository {
    fun enterIfAbsent(memberId: Long, score: Double)

    fun rank(memberId: Long): Long?

    fun count(): Long

    fun popNext(count: Long): List<Long>
}
