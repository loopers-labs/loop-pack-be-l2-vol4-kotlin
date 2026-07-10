package com.loopers.interfaces.api.waitingqueue

import com.fasterxml.jackson.annotation.JsonProperty
import com.loopers.application.waitingqueue.WaitingQueueHealthInfo
import com.loopers.application.waitingqueue.WaitingQueuePollInfo

class WaitingQueueV1Dto {
    data class EnterPollingResponse(
        val status: String,
        val location: String,
        val token: String,
    )

    data class PollResponse(
        val status: String,

        @JsonProperty("left_time")
        val leftTime: Long,

        @JsonProperty("left_people")
        val leftPeople: Long,

        @JsonProperty("next_poll_in")
        val nextPollIn: Long,
    ) {
        companion object {
            fun from(info: WaitingQueuePollInfo): PollResponse =
                PollResponse(
                    status = info.status,
                    leftTime = info.leftTime,
                    leftPeople = info.leftPeople,
                    nextPollIn = info.nextPollIn,
                )
        }
    }

    data class HealthResponse(
        val alive: Boolean,
    ) {
        companion object {
            fun from(info: WaitingQueueHealthInfo): HealthResponse =
                HealthResponse(alive = info.alive)
        }
    }
}
