package com.loopers.application.waitingqueue

import com.loopers.config.waitingqueue.WaitingQueueProperties
import com.loopers.domain.waitingqueue.WaitingQueuePosition
import com.loopers.domain.waitingqueue.WaitingQueueRepository
import org.springframework.stereotype.Component
import kotlin.math.ceil

@Component
class WaitingQueueApplicationService(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val properties: WaitingQueueProperties,
) {
    fun verifyOrEnqueue(userId: Long, token: String?): WaitingQueueDecision {
        if (!properties.enabled) {
            return WaitingQueueDecision.Allowed
        }

        if (!token.isNullOrBlank()) {
            waitingQueueRepository.consumeAllowedToken(userId, token)
            return WaitingQueueDecision.Allowed
        }

        if (!waitingQueueRepository.shouldEnterQueue()) {
            return WaitingQueueDecision.Allowed
        }

        return WaitingQueueDecision.Polling(waitingQueueRepository.enqueue(userId))
    }

    fun poll(userId: Long, token: String): WaitingQueuePollInfo {
        val position = waitingQueueRepository.getPosition(userId, token)
        return when (position) {
            WaitingQueuePosition.Allowed -> WaitingQueuePollInfo(
                status = "allowed",
                leftTime = 0,
                leftPeople = 0,
                nextPollIn = 1,
            )

            is WaitingQueuePosition.Waiting -> WaitingQueuePollInfo(
                status = "waiting",
                leftTime = estimateLeftTime(position.leftPeople),
                leftPeople = position.leftPeople,
                nextPollIn = nextPollInterval(position.leftPeople),
            )
        }
    }

    fun health(): WaitingQueueHealthInfo =
        WaitingQueueHealthInfo(alive = waitingQueueRepository.isAdmissionAlive())

    private fun estimateLeftTime(leftPeople: Long): Long {
        val admitRate = properties.admitRatePerSecond.coerceAtLeast(1)
        return ceil(leftPeople.toDouble() / admitRate.toDouble()).toLong()
    }

    private fun nextPollInterval(leftPeople: Long): Long =
        when {
            leftPeople > 1_000 -> 15
            leftPeople > 100 -> 5
            leftPeople > 20 -> 3
            else -> 1
        }
}
