package com.loopers.domain.waitingqueue

interface WaitingQueueRepository {
    fun shouldEnterQueue(): Boolean

    fun enqueue(userId: Long): String

    fun getPosition(userId: Long, token: String): WaitingQueuePosition

    fun consumeAllowedToken(userId: Long, token: String)

    fun isAdmissionAlive(): Boolean
}

sealed interface WaitingQueuePosition {
    data object Allowed : WaitingQueuePosition

    data class Waiting(
        val leftPeople: Long,
    ) : WaitingQueuePosition
}
