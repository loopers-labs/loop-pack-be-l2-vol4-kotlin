package com.loopers.domain.queue

data class QueuePosition(
    val rank: Long?,
    val totalCount: Long?,
    val estimatedWaitSeconds: Long?,
    val token: EntryToken?,
) {
    companion object {
        fun waiting(rank: Long, totalCount: Long, estimatedWaitSeconds: Long): QueuePosition =
            QueuePosition(
                rank = rank,
                totalCount = totalCount,
                estimatedWaitSeconds = estimatedWaitSeconds,
                token = null,
            )

        fun ready(token: EntryToken): QueuePosition =
            QueuePosition(
                rank = null,
                totalCount = null,
                estimatedWaitSeconds = null,
                token = token,
            )
    }
}
