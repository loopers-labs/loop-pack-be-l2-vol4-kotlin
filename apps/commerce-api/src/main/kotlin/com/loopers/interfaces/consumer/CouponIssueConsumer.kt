package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.coupon.CouponIssueFacade
import com.loopers.application.coupon.CouponIssueProcessor
import com.loopers.config.kafka.KafkaConfig
import com.loopers.domain.coupon.CouponIssueMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * 선착순 쿠폰 발급 요청을 소비해 순차 처리한다.
 * key=couponId 로 같은 쿠폰의 요청이 한 파티션에 모여, 단일 컨슈머가 순서대로 발급한다.
 */
@Component
class CouponIssueConsumer(
    private val couponIssueProcessor: CouponIssueProcessor,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [CouponIssueFacade.TOPIC_COUPON_ISSUE],
        groupId = "\${coupon.consumer.group:commerce-api-coupon-issue}",
        containerFactory = KafkaConfig.BATCH_LISTENER,
        autoStartup = "\${coupon.consumer.enabled:true}",
    )
    fun consume(records: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        records.forEach { record ->
            val message = objectMapper.readValue(record.value(), CouponIssueMessage::class.java)
            couponIssueProcessor.process(message)
        }
        acknowledgment.acknowledge()
    }
}
