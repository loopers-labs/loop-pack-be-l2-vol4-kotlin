package com.loopers.domain.queue

interface WaitingQueueRepository {
    fun addIfAbsent(loginId: String, enteredAtMillis: Long): Boolean
    fun rank(loginId: String): Long?
    fun size(): Long
    fun peekNext(count: Int): List<String>
    fun remove(loginIds: List<String>)
}
