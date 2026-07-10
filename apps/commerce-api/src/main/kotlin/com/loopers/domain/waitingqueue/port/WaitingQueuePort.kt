package com.loopers.domain.waitingqueue.port

import com.loopers.domain.waitingqueue.model.AdmissionTokenCandidate
import com.loopers.domain.waitingqueue.model.WaitingQueueEntryModel
import com.loopers.domain.waitingqueue.model.WaitingQueueState
import java.time.Duration
import java.time.Instant

interface WaitingQueuePort {
    fun findState(userId: Long, now: Instant): WaitingQueueState?

    fun findPosition(userId: Long): Long?

    fun enqueueIfAbsent(userId: Long, now: Instant): WaitingQueueEntryModel

    fun admitNext(
        candidates: List<AdmissionTokenCandidate>,
        tokenTtl: Duration,
        now: Instant,
    ): List<WaitingQueueEntryModel>

    fun validateToken(
        userId: Long,
        token: String,
        idempotencyKey: String,
        now: Instant,
    ): TokenValidationResult

    fun consumeToken(
        userId: Long,
        token: String,
        idempotencyKey: String,
    ): Boolean

    fun releaseToken(
        userId: Long,
        token: String,
        idempotencyKey: String,
    ): Boolean
}
