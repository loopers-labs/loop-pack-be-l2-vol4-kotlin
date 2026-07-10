package com.loopers.application.waitingqueue

data class WaitingQueuePollInfo(
    val status: String,
    val leftTime: Long,
    val leftPeople: Long,
    val nextPollIn: Long,
)

data class WaitingQueueHealthInfo(
    val alive: Boolean,
)
