package com.loopers.domain.waitingqueue.config

import com.loopers.domain.waitingqueue.config.constant.WaitingQueueConfigErrorMessages

data class WaitingQueueRedisKeys(
    val entries: String,
    val sequence: String,
    val userAdmissionPrefix: String,
    val tokenAdmissionPrefix: String,
) {
    init {
        val keys = listOf(entries, sequence, userAdmissionPrefix, tokenAdmissionPrefix)
        require(keys.all { it.isNotBlank() }) { WaitingQueueConfigErrorMessages.REDIS_KEY_REQUIRED }
        require(keys.distinct().size == keys.size) { WaitingQueueConfigErrorMessages.REDIS_KEYS_MUST_BE_DISTINCT }
        require(keys.none { key -> keys.any { other -> key != other && key.startsWith("$other:") } }) {
            WaitingQueueConfigErrorMessages.REDIS_KEYS_MUST_NOT_OVERLAP
        }
    }
}
