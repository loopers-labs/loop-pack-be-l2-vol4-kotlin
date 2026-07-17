package com.loopers.domain.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Outbox 이벤트 생성 및 조회를 담당하는 서비스.
 * 도메인 트랜잭션 내에서 호출하여 이벤트를 안전하게 기록한다.
 */
@Component
class OutboxService(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
) {

    /**
     * 이벤트를 Outbox 테이블에 저장한다.
     * eventId는 UUID로 자동 생성되며, payload는 JSON으로 직렬화된다.
     *
     * @param topic 발행 대상 Kafka 토픽
     * @param partitionKey 파티션 키 (순서 보장 단위)
     * @param payload 이벤트 페이로드 객체
     * @return 저장된 OutboxModel
     */
    @Transactional
    fun save(topic: String, partitionKey: String, payload: Any): OutboxModel {
        val eventId = UUID.randomUUID().toString()
        val json = objectMapper.writeValueAsString(payload)
        return outboxRepository.save(
            OutboxModel(
                topic = topic,
                partitionKey = partitionKey,
                eventId = eventId,
                payload = json,
            ),
        )
    }

    /**
     * 릴레이 대상인 PENDING 상태 이벤트를 조회한다.
     *
     * @param limit 최대 조회 건수 (기본 100)
     * @return PENDING 상태의 Outbox 이벤트 목록
     */
    @Transactional(readOnly = true)
    fun findPendingEvents(limit: Int = 100): List<OutboxModel> {
        return outboxRepository.findPendingEvents(limit)
    }
}
