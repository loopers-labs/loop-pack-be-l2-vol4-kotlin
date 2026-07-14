package com.loopers.domain.waitingqueue.config

import com.loopers.domain.waitingqueue.config.constant.WaitingQueueConfigErrorMessages
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "commerce.waiting-queue")
data class WaitingQueueProperties(
    val schedulerEnabled: Boolean,
    val tokenTtl: Duration,
    val schedulerDelay: Duration,
    val admissionBatchSize: Int,
    val schedulerJitterMax: Duration,
    val pollingInterval: Duration,
    val tokenPrefix: String,
    val redisKeyPrefix: String,
    val redisKeys: WaitingQueueRedisKeys,
) {
    init {
        require(tokenTtl.hasPositiveMillis()) { WaitingQueueConfigErrorMessages.TOKEN_TTL_MUST_BE_AT_LEAST_ONE_MILLISECOND }
        require(schedulerDelay.hasPositiveMillis()) {
            WaitingQueueConfigErrorMessages.SCHEDULER_DELAY_MUST_BE_AT_LEAST_ONE_MILLISECOND
        }
        require(admissionBatchSize > 0) { WaitingQueueConfigErrorMessages.ADMISSION_BATCH_SIZE_MUST_BE_POSITIVE }
        require(!schedulerJitterMax.isNegative) {
            WaitingQueueConfigErrorMessages.SCHEDULER_JITTER_MUST_NOT_BE_NEGATIVE
        }
        require(schedulerJitterMax < tokenTtl) {
            WaitingQueueConfigErrorMessages.SCHEDULER_JITTER_MUST_BE_SHORTER_THAN_TTL
        }
        require(pollingInterval.seconds > 0) { WaitingQueueConfigErrorMessages.POLLING_INTERVAL_MUST_BE_AT_LEAST_ONE_SECOND }
        require(tokenPrefix.isNotBlank()) { WaitingQueueConfigErrorMessages.TOKEN_PREFIX_REQUIRED }
        require(redisKeyPrefix.isNotBlank()) { WaitingQueueConfigErrorMessages.REDIS_KEY_PREFIX_REQUIRED }
    }

    private fun Duration.hasPositiveMillis(): Boolean = !isNegative && toMillis() > 0
}
