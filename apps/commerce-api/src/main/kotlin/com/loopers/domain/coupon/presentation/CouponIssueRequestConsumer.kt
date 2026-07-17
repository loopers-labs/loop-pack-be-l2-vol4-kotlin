package com.loopers.domain.coupon.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.coupon.application.service.CouponIssueRequestWorker
import com.loopers.support.event.CouponIssueRequestedPayload
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestConsumer(
    private val couponIssueRequestWorker: CouponIssueRequestWorker,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = ["\${commerce-events.coupon-issue-request.topic-name}"],
        groupId = CONSUMER_GROUP,
        containerFactory = KafkaConfig.RECORD_LISTENER,
        autoStartup = "\${spring.kafka.listener.auto-startup:true}",
    )
    fun consume(
        event: CouponIssueRequestKafkaEvent,
        acknowledgment: Acknowledgment,
    ) {
        val payload = objectMapper.readValue<CouponIssueRequestedPayload>(event.payload)
        couponIssueRequestWorker.process(
            eventId = event.eventId,
            consumerGroup = CONSUMER_GROUP,
            eventType = event.eventType,
            requestId = payload.requestId,
        )
        acknowledgment.acknowledge()
    }

    companion object {
        const val CONSUMER_GROUP = "commerce-api-coupon-issue"
    }
}
