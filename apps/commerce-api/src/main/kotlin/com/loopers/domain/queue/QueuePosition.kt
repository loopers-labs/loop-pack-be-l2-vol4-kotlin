package com.loopers.domain.queue

data class QueuePosition(
    val rank: Long?,
    val totalCount: Long?,
    val token: EntryToken?,
) {
    companion object {
        fun waiting(rank: Long, totalCount: Long): QueuePosition =
            QueuePosition(rank = rank, totalCount = totalCount, token = null)

        fun ready(token: EntryToken): QueuePosition =
            QueuePosition(rank = null, totalCount = null, token = token)
    }
}
