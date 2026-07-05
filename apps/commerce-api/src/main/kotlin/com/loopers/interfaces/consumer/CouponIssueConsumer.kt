package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.coupon.usecase.IssueCouponFromRequestUsecase
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CouponIssueConsumer(
    private val objectMapper: ObjectMapper,
    private val issueUsecase: IssueCouponFromRequestUsecase,
) {
    private val log = LoggerFactory.getLogger(CouponIssueConsumer::class.java)

    @KafkaListener(
        topics = ["coupon-issue-requests"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(records: List<ConsumerRecord<String, ByteArray>>, acknowledgment: Acknowledgment) {
        records.forEach { record ->
            runCatching {
                val node = objectMapper.readTree(String(record.value(), Charsets.UTF_8))
                issueUsecase.issue(
                    requestId = node["requestId"].asText(),
                    userId = node["userId"].asLong(),
                    couponId = node["couponId"].asLong(),
                )
            }.onFailure {
                // ponytail: per-record runCatching + ack만으로는 일시적 실패가 재시도되지 않는다.
                // 재시도가 필요하면 DefaultErrorHandler + DLQ 도입이 필요하며 이번 라운드 범위 밖이다.
                log.error(
                    "Failed to process coupon-issue record. topic={} partition={} offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    it,
                )
            }
        }
        acknowledgment.acknowledge()
    }
}
