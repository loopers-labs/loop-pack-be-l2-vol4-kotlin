package com.loopers.domain.queue

sealed class QueueStatus {
    data class Waiting(val position: Long, val totalWaiting: Long, val estimatedWaitSeconds: Long) : QueueStatus()
    data class Ready(val token: String) : QueueStatus()
    data object NotInQueue : QueueStatus()
}
