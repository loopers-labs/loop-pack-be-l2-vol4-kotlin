package com.loopers.domain.waitingqueue.model

import com.loopers.domain.waitingqueue.constant.WaitingQueueErrorMessages

data class WaitingQueueEntryModel(
    val userId: Long,
    val sequence: Long,
    val status: WaitingQueueStatus,
) {
    init {
        require(sequence > 0) { WaitingQueueErrorMessages.SEQUENCE_MUST_BE_POSITIVE }
    }
}
