package com.loopers.outbox.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.outbox.domain.EventMessagePublisher
import com.loopers.outbox.domain.EventTopics
import com.loopers.outbox.domain.OutboxEventRepository
import com.loopers.outbox.domain.OutboxStatus
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
@ConditionalOnProperty("outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelay(
    private val outboxEventRepository: OutboxEventRepository,
    private val eventMessagePublisher: EventMessagePublisher,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(OutboxRelay::class.java)

    @Scheduled(fixedDelayString = "\${outbox.relay.fixed-delay:1000}")
    fun relay() {
        val pending = outboxEventRepository.findByStatus(OutboxStatus.INIT, limit = 100)
        if (pending.isEmpty()) {
            return
        }
        val sentIds = mutableListOf<Long>()
        for (event in pending) {
            try {
                eventMessagePublisher.publish(
                    topic = EventTopics.forAggregateType(event.aggregateType),
                    partitionKey = event.aggregateId.toString(),
                    message = objectMapper.readTree(event.payload),
                )
                sentIds += event.id
            } catch (e: CallNotPermittedException) {
                logger.warn("카프카 서킷 OPEN — 발행 보류, 다음 폴링에서 재시도 (id={})", event.id)
                break
            } catch (e: Exception) {
                logger.warn(
                    "outbox 발행 실패 — 중단 후 재시도 대기 (id={}, eventType={}): {}",
                    event.id,
                    event.eventType,
                    e.javaClass.simpleName,
                )
                break
            }
        }
        if (sentIds.isNotEmpty()) {
            outboxEventRepository.markSent(sentIds)
        }
    }

    @Scheduled(fixedDelayString = "\${outbox.purge.fixed-delay:3600000}")
    fun purgeSent() {
        val deleted = outboxEventRepository.deleteSentBefore(ZonedDateTime.now().minusDays(3))
        if (deleted > 0) {
            logger.info("SENT outbox {}건 정리", deleted)
        }
    }
}
