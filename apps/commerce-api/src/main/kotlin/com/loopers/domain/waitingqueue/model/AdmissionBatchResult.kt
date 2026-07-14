package com.loopers.domain.waitingqueue.model

data class AdmissionBatchResult(
    val admittedCount: Int,
    val admittedUserIds: List<Long>,
)
