package com.loopers.domain.waitingqueue.model

import com.loopers.domain.waitingqueue.constant.WaitingQueueErrorMessages
import java.time.Instant

data class AdmissionTokenCandidate(
    val token: String,
    val availableAt: Instant,
    val expiresAt: Instant,
) {
    init {
        require(token.isNotBlank()) { WaitingQueueErrorMessages.TOKEN_REQUIRED }
        require(availableAt.isBefore(expiresAt)) { WaitingQueueErrorMessages.AVAILABLE_AT_AFTER_EXPIRATION }
    }
}
