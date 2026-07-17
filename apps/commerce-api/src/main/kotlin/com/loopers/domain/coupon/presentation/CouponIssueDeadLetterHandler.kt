package com.loopers.domain.coupon.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.CouponIssueTopicProperties
import com.loopers.config.kafka.KafkaDeadLetterHandler
import com.loopers.domain.coupon.application.service.CouponIssueRequestFailureService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CouponIssueDeadLetterHandler(
    private val properties: CouponIssueTopicProperties,
    private val objectMapper: ObjectMapper,
    private val failureService: CouponIssueRequestFailureService,
) : KafkaDeadLetterHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(record: ConsumerRecord<*, *>): Boolean = record.topic() == properties.topicName

    override fun destination(record: ConsumerRecord<*, *>, exception: Exception): TopicPartition =
        TopicPartition(properties.dltTopicName, record.partition())

    override fun afterPublished(record: ConsumerRecord<*, *>, exception: Exception) {
        val requestId = parseRequestId(record) ?: return
        failureService.markFailed(
            requestId = requestId,
            reason = exception.message ?: exception.javaClass.simpleName,
        )
    }

    private fun parseRequestId(record: ConsumerRecord<*, *>): UUID? =
        try {
            val event = when (val value = record.value()) {
                is ByteArray -> objectMapper.readValue(value, CouponIssueRequestKafkaEvent::class.java)
                is CouponIssueRequestKafkaEvent -> value
                else -> return null
            }
            UUID.fromString(objectMapper.readTree(event.payload).path("requestId").asText())
        } catch (exception: Exception) {
            log.warn("failed to read coupon issue request from dead letter record: topic={}", record.topic(), exception)
            null
        }
}
