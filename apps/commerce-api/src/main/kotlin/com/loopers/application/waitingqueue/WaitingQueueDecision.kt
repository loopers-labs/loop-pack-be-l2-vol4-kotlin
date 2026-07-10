package com.loopers.application.waitingqueue

sealed interface WaitingQueueDecision {
    data object Allowed : WaitingQueueDecision

    data class Polling(
        val token: String,
    ) : WaitingQueueDecision
}
