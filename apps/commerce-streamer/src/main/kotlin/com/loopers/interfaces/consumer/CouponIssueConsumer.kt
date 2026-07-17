package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.Topics
import com.loopers.config.kafka.event.CouponIssueRequestMessage
import com.loopers.domain.coupon.CouponIssueProcessor
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * coupon-issue-requests 토픽 Consumer.
 * 선착순 쿠폰 발급 요청을 순차적으로 처리한다.
 * 파티션 키가 couponId이므로 같은 쿠폰에 대한 요청은 순서대로 처리되어 동시성 문제를 줄인다.
 */
@Component
class CouponIssueConsumer(
    private val couponIssueProcessor: CouponIssueProcessor,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 쿠폰 발급 요청 메시지를 소비하여 CouponIssueProcessor에 위임한다.
     * Processor에서 수량 확인, 중복 방지, 실제 발급을 처리한다.
     */
    @KafkaListener(
        topics = [Topics.COUPON_ISSUE_REQUESTS],
        groupId = "streamer-coupon",
        containerFactory = "singleListenerContainerFactory",
    )
    fun consume(record: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val message = objectMapper.readValue(record.value(), CouponIssueRequestMessage::class.java)

        log.info(
            "[Consumer] 쿠폰 발급 요청 처리 시작 (requestId={}, couponId={}, userId={})",
            message.requestId,
            message.couponId,
            message.userId,
        )

        couponIssueProcessor.process(message)
        acknowledgment.acknowledge()
    }
}
