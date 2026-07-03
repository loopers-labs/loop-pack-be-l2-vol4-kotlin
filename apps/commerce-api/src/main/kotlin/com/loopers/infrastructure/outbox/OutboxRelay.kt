package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.OutboxEventRepository
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OutboxRelay(
    private val outboxRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {
    companion object {
        private const val BATCH_SIZE = 100
    }

    // PENDING 행을 순서대로 발행하고 SENT 전이. 발행 실패 시 PENDING 유지 → 다음 주기 재시도(at-least-once).
    @Transactional
    fun relayOnce(): Int {
        val pending = outboxRepository.findTopPending(BATCH_SIZE)
        var sent = 0
        for (e in pending) {
            // acks=all + idempotence=true 프로듀서. 동기 대기로 발행 확인 후 SENT.
            // ponytail: 동기 전송, 처리량 필요 시 비동기 배치로 업그레이드.
            kafkaTemplate.send(e.topic, e.partitionKey, e.payload).get()
            e.markSent()
            outboxRepository.save(e)
            sent++
        }
        return sent
    }
}
