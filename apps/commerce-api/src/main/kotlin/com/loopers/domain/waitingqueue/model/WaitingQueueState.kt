package com.loopers.domain.waitingqueue.model

import java.time.Instant

data class WaitingQueueState(
    val status: WaitingQueueStatus,
    val entry: WaitingQueueEntryModel,
    val sequence: Long,
    val position: Long?,
    val totalWaiting: Long,
    val estimatedWaitSeconds: Long,
    val recommendedPollingIntervalSeconds: Long,
    val token: String?,
    val tokenAvailableAt: Instant?,
    val tokenExpiresAt: Instant?,
)
