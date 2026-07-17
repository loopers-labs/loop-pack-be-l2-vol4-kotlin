package com.loopers.support.outbox

import java.time.ZonedDateTime
import java.util.UUID

data class OutboxEventModel(
    val id: Long = 0L,
    val eventId: UUID = UUID.randomUUID(),
    val type: String,
    val aggregateType: String,
    val aggregateId: Long,
    val topicName: String? = null,
    val partitionKey: String? = null,
    val payload: String,
    val status: OutboxEventStatus = OutboxEventStatus.PENDING,
    val retryCount: Int = 0,
    val nextRetryAt: ZonedDateTime? = null,
    val lastError: String? = null,
    val publishedAt: ZonedDateTime? = null,
    val claimId: UUID? = null,
    val claimedAt: ZonedDateTime? = null,
    val createdAt: ZonedDateTime = ZonedDateTime.now(),
) {
    init {
        require((topicName == null) == (partitionKey == null)) {
            "topicName and partitionKey must both be present or both be absent."
        }
        require(topicName?.isNotBlank() != false) { "topicName must not be blank." }
        require(partitionKey?.isNotBlank() != false) { "partitionKey must not be blank." }
    }

    val publishable: Boolean
        get() = topicName != null

    companion object {
        fun publishable(
            type: String,
            aggregateType: String,
            aggregateId: Long,
            topicName: String,
            partitionKey: String,
            payload: String,
            eventId: UUID = UUID.randomUUID(),
            createdAt: ZonedDateTime = ZonedDateTime.now(),
        ): OutboxEventModel = OutboxEventModel(
            eventId = eventId,
            type = type,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            topicName = topicName,
            partitionKey = partitionKey,
            payload = payload,
            createdAt = createdAt,
        )

        fun internal(
            type: String,
            aggregateType: String,
            aggregateId: Long,
            payload: String,
            eventId: UUID = UUID.randomUUID(),
            createdAt: ZonedDateTime = ZonedDateTime.now(),
        ): OutboxEventModel = OutboxEventModel(
            eventId = eventId,
            type = type,
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            payload = payload,
            createdAt = createdAt,
        )
    }
}
