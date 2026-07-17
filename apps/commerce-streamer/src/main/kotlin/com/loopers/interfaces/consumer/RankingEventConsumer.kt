package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.config.kafka.KafkaConfig
import com.loopers.projection.ranking.application.RankingProjectionCommand
import com.loopers.projection.ranking.application.RankingProjectionService
import com.loopers.projection.ranking.application.RankingScoreDelta
import com.loopers.projection.ranking.application.RankingScorePolicy
import java.time.ZonedDateTime
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class RankingEventConsumer(
    private val rankingProjectionService: RankingProjectionService,
    private val rankingScorePolicy: RankingScorePolicy,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = [
            "\${commerce-events.product-metrics.catalog-topic-name}",
            "\${commerce-events.product-metrics.order-topic-name}",
        ],
        groupId = CONSUMER_GROUP,
        containerFactory = KafkaConfig.RECORD_LISTENER,
    )
    fun consume(
        event: ProductMetricsKafkaEvent,
        acknowledgment: Acknowledgment,
    ) {
        event.toCommandOrNull()?.let { rankingProjectionService.project(it) }
        acknowledgment.acknowledge()
    }

    private fun ProductMetricsKafkaEvent.toCommandOrNull(): RankingProjectionCommand? {
        val occurredAt = createdAt?.let { ZonedDateTime.parse(it) } ?: ZonedDateTime.now()
        val deltas = when (eventType) {
            LIKE_COUNT_CHANGED_EVENT_TYPE -> listOf(likeScoreDelta())

            ORDER_PAID_EVENT_TYPE -> orderPaidPayload().items.map {
                RankingScoreDelta(
                    productId = it.productId,
                    score = rankingScorePolicy.orderScore(),
                )
            }

            else -> return null
        }
        return RankingProjectionCommand(
            eventId = eventId,
            occurredAt = occurredAt,
            deltas = deltas,
        )
    }

    private fun ProductMetricsKafkaEvent.likeScoreDelta(): RankingScoreDelta {
        val parsed = payload?.let { objectMapper.readValue<LikeCountChangedPayload>(it) } ?: LikeCountChangedPayload(
            productId = requireNotNull(productId) { "productId is required." },
            userId = userId,
            delta = requireNotNull(delta) { "delta is required." },
        )
        val likeDelta = requireNotNull(parsed.delta) { "delta is required." }
        require(likeDelta == -1 || likeDelta == 1) { "delta must be -1 or 1." }
        return RankingScoreDelta(
            productId = requireNotNull(parsed.productId) { "productId is required." },
            score = rankingScorePolicy.likeScore(likeDelta),
        )
    }

    private fun ProductMetricsKafkaEvent.orderPaidPayload(): OrderPaidPayload =
        payload?.let { objectMapper.readValue(it) } ?: OrderPaidPayload(items = emptyList())

    companion object {
        const val CONSUMER_GROUP = "commerce-streamer-ranking"
        const val LIKE_COUNT_CHANGED_EVENT_TYPE = "LIKE_COUNT_CHANGED_V1"
        const val ORDER_PAID_EVENT_TYPE = "ORDER_PAID_V1"
    }
}
