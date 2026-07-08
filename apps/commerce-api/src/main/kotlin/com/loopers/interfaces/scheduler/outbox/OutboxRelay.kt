package com.loopers.interfaces.scheduler.outbox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.outbox.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Transactional Outbox 릴레이.
 * PENDING 이벤트를 조회해 Kafka 로 발행하고, 성공한 건만 PUBLISHED 로 마킹한다.
 * 발행 성공 후 마킹 전 크래시 시 다음 주기에 재발행 → At Least Once.
 *
 * 외부 I/O(Kafka 발행)를 DB 트랜잭션 밖에서 수행하고, 마킹만 짧은 트랜잭션으로 처리한다.
 *
 * 발행 실패는 이벤트별 catch 로 격리되어 다른 이벤트를 막지 않고, 실패분은 PENDING 으로 남아 다음 주기에 재시도된다.
 * payload 는 이 앱이 직렬화한 JSON 을 되읽는 것이라 파싱 실패(poison)는 사실상 없고, 현실적 실패인 브로커 일시 장애는
 * 무한 재시도가 곧 At Least Once 보장이 된다. 따라서 별도 DLQ/attempt_count 는 (현 규모에선) 도입하지 않는다.
 *
 * 다중 인스턴스 배포 시 @Scheduled 가 인스턴스별 독립 실행되어 같은 PENDING 을 중복 발행할 수 있으나, 소비측 event_handled
 * 멱등으로 흡수된다(중복 소비 없음, Kafka 트래픽 낭비만). 분산 락(ShedLock 등)은 스케일아웃 시점에 재검토한다.
 */
@Component
class OutboxRelay(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${outbox.relay.fixed-delay-ms:2000}")
    fun relay() {
        val batch = outboxEventRepository.findPendingBatch(BATCH_SIZE)
        if (batch.isEmpty()) return

        val publishedIds = mutableListOf<Long>()
        for (event in batch) {
            try {
                // payload 는 이미 JSON 문자열이므로, JsonNode 로 파싱해 보내야 이중 인코딩을 피한다.
                val message: JsonNode = objectMapper.readTree(event.payload)
                kafkaTemplate.send(event.topic, event.aggregateId.toString(), message)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                publishedIds += event.id
            } catch (e: Exception) {
                // graceful shutdown 등으로 인터럽트되면 플래그를 복원해 스케줄러 스레드가 정상 종료되게 한다.
                if (e is InterruptedException) Thread.currentThread().interrupt()
                log.warn("Outbox 발행 실패 (다음 주기 재시도): eventId={}, topic={}", event.eventId, event.topic, e)
            }
        }
        outboxEventRepository.markPublished(publishedIds)
    }

    companion object {
        private const val BATCH_SIZE = 100
        private const val SEND_TIMEOUT_SECONDS = 5L
    }
}
