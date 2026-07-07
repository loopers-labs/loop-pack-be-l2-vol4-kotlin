package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.coupon.FirstComeIssueFacade
import com.loopers.config.kafka.KafkaConfig
import com.loopers.kafka.EventEnvelope
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * coupon-issue-requests 소비자 — 접수된 선착순 발급 요청을 처리한다.
 * 요청 식별자만 꺼내 처리기로 넘기고(발급 근거는 접수 레코드), 배치 처리 완료 후 수동 ack 한다.
 * 처리 도중 실패하면 ack 하지 않아 재전달로 복구되며, 이미 확정된 요청은 처리기가 멱등하게 건너뛴다.
 * 형식이 깨진 메시지(역직렬화 불가·requestId 누락)는 재전달해도 영영 실패하므로 기록만 남기고 건너뛴다.
 */
@Component
class CouponIssueRequestConsumer(
    private val firstComeIssueFacade: FirstComeIssueFacade,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(CouponIssueRequestConsumer::class.java)

    @KafkaListener(
        topics = ["\${loopers.kafka.topic.coupon-issue-requests}"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        records: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        records.forEach { record ->
            val requestId = requestIdOf(record.value()) ?: return@forEach
            firstComeIssueFacade.handle(requestId)
        }
        acknowledgment.acknowledge()
    }

    private fun requestIdOf(message: ByteArray): String? {
        val envelope = runCatching { objectMapper.readValue(message, EventEnvelope::class.java) }
            .getOrElse { e ->
                log.error("역직렬화할 수 없는 메시지 — 건너뛴다", e)
                return null
            }
        val requestId = envelope.payload.path("requestId").asText("")
        if (requestId.isBlank()) {
            log.error("requestId 없는 발급 요청 이벤트 — 건너뛴다: eventId={}", envelope.eventId)
            return null
        }
        return requestId
    }
}
