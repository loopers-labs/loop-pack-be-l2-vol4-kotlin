package com.loopers.domain.waitingqueue.support

import com.loopers.domain.waitingqueue.config.WaitingQueueProperties
import com.loopers.domain.waitingqueue.config.WaitingQueueRedisKeys
import com.loopers.domain.waitingqueue.model.WaitingQueueEntryModel
import com.loopers.domain.waitingqueue.model.WaitingQueueState
import com.loopers.domain.waitingqueue.model.WaitingQueueStatus
import java.time.Duration
import java.time.Instant

class WaitingQueueSteps {
    companion object {
        const val 기본_사용자_ID: Long = 1L
        const val 다른_사용자_ID: Long = 2L
        const val 세번째_사용자_ID: Long = 3L
        const val 기본_SEQUENCE: Long = 11L
        const val 기본_토큰: String = "q_round8_token_1"
        const val 기본_멱등키: String = "order-idempotency-key-1"
        val 기준_시각: Instant = Instant.parse("2026-07-05T00:00:00Z")

        fun 대기열_설정(
            tokenTtl: Duration = Duration.ofMinutes(5),
            schedulerDelay: Duration = Duration.ofSeconds(10),
            admissionBatchSize: Int = 3,
            schedulerJitterMax: Duration = Duration.ofSeconds(3),
            pollingInterval: Duration = Duration.ofSeconds(2),
            tokenPrefix: String = "q_",
            redisKeyPrefix: String = "waiting-queue",
        ): WaitingQueueProperties = WaitingQueueProperties(
            schedulerEnabled = true,
            tokenTtl = tokenTtl,
            schedulerDelay = schedulerDelay,
            admissionBatchSize = admissionBatchSize,
            schedulerJitterMax = schedulerJitterMax,
            pollingInterval = pollingInterval,
            tokenPrefix = tokenPrefix,
            redisKeyPrefix = redisKeyPrefix,
            redisKeys = WaitingQueueRedisKeys(
                entries = "entries",
                sequence = "sequence",
                userAdmissionPrefix = "admission:user",
                tokenAdmissionPrefix = "admission:token",
            ),
        )

        fun 대기열_항목(
            userId: Long = 기본_사용자_ID,
            sequence: Long = 기본_SEQUENCE,
            status: WaitingQueueStatus = WaitingQueueStatus.WAITING,
        ): WaitingQueueEntryModel = WaitingQueueEntryModel(
            userId = userId,
            sequence = sequence,
            status = status,
        )

        fun 대기중_상태(
            entry: WaitingQueueEntryModel = 대기열_항목(),
            position: Long = 1L,
            totalWaiting: Long = 1L,
            estimatedWaitSeconds: Long = 0L,
            recommendedPollingIntervalSeconds: Long = 2L,
        ): WaitingQueueState = WaitingQueueState(
            status = WaitingQueueStatus.WAITING,
            entry = entry,
            sequence = entry.sequence,
            position = position,
            totalWaiting = totalWaiting,
            estimatedWaitSeconds = estimatedWaitSeconds,
            recommendedPollingIntervalSeconds = recommendedPollingIntervalSeconds,
            token = null,
            tokenAvailableAt = null,
            tokenExpiresAt = null,
        )

        fun 입장_상태(
            entry: WaitingQueueEntryModel = 대기열_항목(status = WaitingQueueStatus.ADMITTED),
            token: String = 기본_토큰,
            tokenAvailableAt: Instant = 기준_시각,
            tokenExpiresAt: Instant = 기준_시각.plus(Duration.ofMinutes(5)),
        ): WaitingQueueState = WaitingQueueState(
            status = WaitingQueueStatus.ADMITTED,
            entry = entry,
            sequence = entry.sequence,
            position = null,
            totalWaiting = 0L,
            estimatedWaitSeconds = 0L,
            recommendedPollingIntervalSeconds = 2L,
            token = token,
            tokenAvailableAt = tokenAvailableAt,
            tokenExpiresAt = tokenExpiresAt,
        )
    }
}
