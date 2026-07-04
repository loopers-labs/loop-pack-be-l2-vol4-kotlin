package com.loopers.application.like

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaTopics
import com.loopers.domain.event.UserActionLogPayload
import com.loopers.domain.event.UserActionType
import com.loopers.domain.like.LikeEvent
import com.loopers.domain.like.LikeService
import com.loopers.domain.outbox.OutboxModel
import com.loopers.domain.outbox.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class LikeEventHandler(
    private val likeService: LikeService,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun toOutbox(event: LikeEvent.Changed) {
        outboxRepository.save(
            OutboxModel.of(
                aggregateId = event.productId.toString(),
                eventType = "LikeChanged",
                topic = KafkaTopics.CATALOG_EVENTS,
                payload = objectMapper.writeValueAsString(event),
            ),
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun toActionLog(event: LikeEvent.Changed) {
        val actionType = if (event.type == LikeEvent.LikeChangeType.LIKED) UserActionType.LIKED else UserActionType.UNLIKED
        outboxRepository.save(
            OutboxModel.of(
                aggregateId = event.userId.toString(),
                eventType = "UserActionLogged",
                topic = KafkaTopics.USER_ACTION_EVENTS,
                payload = objectMapper.writeValueAsString(
                    UserActionLogPayload(
                        eventId = event.eventId,
                        userId = event.userId,
                        actionType = actionType,
                        targetId = event.productId,
                        occurredAt = event.occurredAt,
                    ),
                ),
            ),
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun applyMetrics(event: LikeEvent.Changed) {
        runCatching { likeService.applyLikeCount(event) }
            .onFailure { log.warn("좋아요 집계 실패 productId={}, {}", event.productId, it.message) }
    }
}
