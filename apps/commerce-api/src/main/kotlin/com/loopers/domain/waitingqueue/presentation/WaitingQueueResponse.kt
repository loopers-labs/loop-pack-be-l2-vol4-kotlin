package com.loopers.domain.waitingqueue.presentation

import com.loopers.domain.waitingqueue.model.WaitingQueueState
import java.time.Instant

data class WaitingQueueResponse(
    val status: String,
    val position: Long?,
    val totalWaiting: Long,
    val estimatedWaitSeconds: Long,
    val recommendedPollingIntervalSeconds: Long,
    val token: String?,
    val tokenAvailableAt: Instant?,
    val tokenExpiresAt: Instant?,
) {
    companion object {
        fun from(state: WaitingQueueState): WaitingQueueResponse = WaitingQueueResponse(
            status = state.status.name,
            position = state.position,
            totalWaiting = state.totalWaiting,
            estimatedWaitSeconds = state.estimatedWaitSeconds,
            recommendedPollingIntervalSeconds = state.recommendedPollingIntervalSeconds,
            token = state.token,
            tokenAvailableAt = state.tokenAvailableAt,
            tokenExpiresAt = state.tokenExpiresAt,
        )
    }
}
