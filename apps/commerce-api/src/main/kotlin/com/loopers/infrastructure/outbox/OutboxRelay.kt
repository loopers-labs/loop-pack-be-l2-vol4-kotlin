package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.kafka.EventEnvelope
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Transactional Outbox 릴레이 — 미발행(PENDING) 아웃박스를 Kafka 로 발행한다.
 * 브로커 ack(`.get()`) 이후에만 PUBLISHED 로 전이하고, 실패는 PENDING 으로 남겨 다음 주기에 재시도한다(At Least Once).
 * `aggregateId` 를 파티션 key 로 써 같은 애그리거트 이벤트의 순서를 파티션 단위로 보장한다.
 * 같은 애그리거트의 앞선 이벤트가 실패하면 그 애그리거트의 뒤 이벤트는 이번 주기에 발행하지 않는다 —
 * 뒤 이벤트만 먼저 나가면 파티션 안에서 순서가 뒤집히기 때문이다(예: 같은 쿠폰의 선착순 요청 역전).
 * 한 주기는 배치 상한(BATCH_SIZE)·발행 대기 상한(SEND_TIMEOUT)으로 묶어 트랜잭션이 브로커 지연에 무한정 늘어나지 않게 한다.
 * 주기 구동은 [OutboxRelayScheduler] 가 담당한다 — 통합 테스트는 이 함수를 직접 호출해 발행을 명시 제어한다.
 */
@Component
class OutboxRelay(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(OutboxRelay::class.java)

    @Transactional
    fun relay() {
        val pending = outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, Pageable.ofSize(BATCH_SIZE))
        val failedAggregates = mutableSetOf<Pair<String, String>>()
        pending.forEach { event ->
            val aggregateKey = event.aggregateType to event.aggregateId
            if (aggregateKey in failedAggregates) {
                return@forEach // 같은 애그리거트의 앞선 이벤트가 실패 — 순서 보존을 위해 이번 주기는 건너뛴다
            }
            runCatching {
                kafkaTemplate.send(topicOf(event.aggregateType), event.aggregateId, envelopeOf(event))
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }.onSuccess {
                event.markPublished(LocalDateTime.now())
            }.onFailure { e ->
                failedAggregates += aggregateKey
                log.warn("outbox relay publish failed, keep PENDING for retry: eventId={}", event.eventId, e)
            }
        }
    }

    private fun envelopeOf(event: OutboxEventEntity): EventEnvelope = EventEnvelope(
        eventId = event.eventId,
        eventType = event.eventType,
        aggregateType = event.aggregateType,
        aggregateId = event.aggregateId,
        occurredAt = event.occurredAt,
        payload = objectMapper.readTree(event.payload),
    )

    private fun topicOf(aggregateType: String): String = when (aggregateType) {
        "ORDER" -> ORDER_EVENTS
        "PRODUCT" -> CATALOG_EVENTS
        "COUPON_ISSUE_REQUEST" -> COUPON_ISSUE_REQUESTS
        else -> error("unknown aggregateType for outbox routing: $aggregateType")
    }

    companion object {
        const val ORDER_EVENTS = "order-events"
        const val CATALOG_EVENTS = "catalog-events"
        const val COUPON_ISSUE_REQUESTS = "coupon-issue-requests"

        private const val BATCH_SIZE = 500
        private const val SEND_TIMEOUT_SECONDS = 10L
    }
}
