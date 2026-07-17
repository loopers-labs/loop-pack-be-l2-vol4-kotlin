package com.loopers.failure.infrastructure

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "consumed_event_failure",
    indexes = [Index(name = "idx_cef_topic_created", columnList = "original_topic, created_at")],
)
class ConsumedEventFailure(
    originalTopic: String,
    originalPartition: Int?,
    originalOffset: Long?,
    consumerGroup: String?,
    exceptionFqcn: String?,
    exceptionMessage: String?,
    payload: String,
) : BaseEntity() {
    @Column(name = "original_topic", nullable = false, length = 60, updatable = false)
    val originalTopic: String = originalTopic

    @Column(name = "original_partition", updatable = false)
    val originalPartition: Int? = originalPartition

    @Column(name = "original_offset", updatable = false)
    val originalOffset: Long? = originalOffset

    @Column(name = "consumer_group", length = 80, updatable = false)
    val consumerGroup: String? = consumerGroup

    @Column(name = "exception_fqcn", length = 200, updatable = false)
    val exceptionFqcn: String? = exceptionFqcn

    @Column(name = "exception_message", columnDefinition = "TEXT", updatable = false)
    val exceptionMessage: String? = exceptionMessage

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT", updatable = false)
    val payload: String = payload
}
