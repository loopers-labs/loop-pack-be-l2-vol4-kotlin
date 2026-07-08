package com.loopers.domain.queue

data class WaitingQueuePosition(
    val status: WaitingQueueStatus,
    val rank: Long?,
    val totalWaiting: Long,
    val estimatedWaitSeconds: Long?,
    val pollingIntervalSeconds: Long,
    val entryToken: String?,
)
