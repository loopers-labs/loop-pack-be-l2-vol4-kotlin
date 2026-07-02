package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.coupon.CouponIssueProcessor
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CouponIssueConsumer(
    private val couponIssueProcessor: CouponIssueProcessor,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["\${coupon.issue.topic}"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        messages: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        messages.forEach { record ->
            runCatching {
                val payload = objectMapper.readValue(record.value(), CouponIssuePayload::class.java)
                couponIssueProcessor.process(payload)
            }.onFailure {
                log.error("쿠폰 발급 메시지 처리 실패 offset={}", record.offset(), it)
            }
        }
        acknowledgment.acknowledge()
    }
}
