package com.loopers.domain.waitingqueue

import kotlin.math.ceil

data class WaitingQueuePosition(
    val status: WaitingQueueStatus,
    val rank: Long?,
    val currentTotalWaitingCount: Long,
    val estimatedWaitSeconds: Long?,
    val pollingIntervalSeconds: Long,
    val entryToken: String?,
) {
    companion object {
        fun waiting(
            rank: Long,
            currentTotalWaitingCount: Long,
        ): WaitingQueuePosition {
            return WaitingQueuePosition(
                status = WaitingQueueStatus.WAITING,
                rank = rank,
                currentTotalWaitingCount = currentTotalWaitingCount,
                estimatedWaitSeconds = estimatedWaitSeconds(rank),
                pollingIntervalSeconds = pollingIntervalSeconds(rank),
                entryToken = null,
            )
        }

        fun ready(
            token: String,
            currentTotalWaitingCount: Long,
        ): WaitingQueuePosition {
            return WaitingQueuePosition(
                status = WaitingQueueStatus.READY,
                rank = 0,
                currentTotalWaitingCount = currentTotalWaitingCount,
                estimatedWaitSeconds = 0,
                pollingIntervalSeconds = 0,
                entryToken = token,
            )
        }

        fun notEntered(currentTotalWaitingCount: Long): WaitingQueuePosition {
            return WaitingQueuePosition(
                status = WaitingQueueStatus.NOT_ENTERED,
                rank = null,
                currentTotalWaitingCount = currentTotalWaitingCount,
                estimatedWaitSeconds = null,
                pollingIntervalSeconds = pollingIntervalSeconds(null),
                entryToken = null,
            )
        }

        private fun estimatedWaitSeconds(rank: Long): Long {
            return ceil(rank.toDouble() / ESTIMATED_THROUGHPUT_PER_SECOND.toDouble()).toLong()
        }

        private fun pollingIntervalSeconds(rank: Long?): Long {
            return when {
                rank == null -> 5
                rank < 100 -> 1
                rank < 1_000 -> 3
                else -> 5
            }
        }

        private const val ESTIMATED_THROUGHPUT_PER_SECOND = 50L
    }
}
