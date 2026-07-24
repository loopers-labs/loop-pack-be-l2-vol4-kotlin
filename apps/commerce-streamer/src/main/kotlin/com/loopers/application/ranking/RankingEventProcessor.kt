package com.loopers.application.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.IncomingEvent
import com.loopers.application.metrics.OrderMetricPayload
import com.loopers.application.metrics.ProductMetricPayload
import com.loopers.domain.ranking.RankingScorePolicy
import com.loopers.infrastructure.ranking.DailyProductKey
import com.loopers.infrastructure.ranking.DailyProductRankingMetricsJpaRepository
import com.loopers.infrastructure.ranking.RankingEventHandledJpaEntity
import com.loopers.infrastructure.ranking.RankingEventHandledJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

@Component
class RankingEventProcessor(
    private val metricsRepository: DailyProductRankingMetricsJpaRepository,
    private val eventHandledRepository: RankingEventHandledJpaRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun process(events: List<IncomingEvent>): Map<DailyProductKey, Double> {
        val distinctEvents = events.distinctBy { it.eventId }
        val affectedKeys = collectAffectedKeys(distinctEvents)

        val handledIds = eventHandledRepository.findAllById(distinctEvents.map { it.eventId })
            .map { it.eventId }
            .toSet()
        val newEvents = distinctEvents.filter { it.eventId !in handledIds }

        if (newEvents.isNotEmpty()) {
            applyEvents(newEvents)
        }

        if (affectedKeys.isEmpty()) return emptyMap()

        return affectedKeys.groupBy({ it.date }, { it.productId })
            .flatMap { (date, productIds) ->
                metricsRepository.findByMetricDateAndProductIdIn(date, productIds)
            }
            .associate { DailyProductKey(it.productId, it.metricDate) to it.rankingScore }
    }

    private fun applyEvents(newEvents: List<IncomingEvent>) {
        val aggregated = mutableMapOf<DailyProductKey, MetricsDelta>()

        for (event in newEvents) {
            val date = eventDate(event)
            when (event.eventType) {
                LIKE_INCREASED -> {
                    val payload = objectMapper.convertValue(event.payload, ProductMetricPayload::class.java)
                    aggregated.getOrPut(DailyProductKey(payload.productId, date)) { MetricsDelta() }.apply {
                        likeCount += 1
                        score += RankingScorePolicy.LIKE_SCORE
                    }
                }
                LIKE_DECREASED -> {
                    val payload = objectMapper.convertValue(event.payload, ProductMetricPayload::class.java)
                    aggregated.getOrPut(DailyProductKey(payload.productId, date)) { MetricsDelta() }.apply {
                        likeCount -= 1
                        score -= RankingScorePolicy.LIKE_SCORE
                    }
                }
                PRODUCT_VIEWED -> {
                    val payload = objectMapper.convertValue(event.payload, ProductMetricPayload::class.java)
                    aggregated.getOrPut(DailyProductKey(payload.productId, date)) { MetricsDelta() }.apply {
                        viewCount += 1
                        score += RankingScorePolicy.VIEW_SCORE
                    }
                }
                PAYMENT_SUCCESS -> {
                    val payload = objectMapper.convertValue(event.payload, OrderMetricPayload::class.java)
                    payload.items.forEach { item ->
                        aggregated.getOrPut(DailyProductKey(item.productId, date)) { MetricsDelta() }.apply {
                            orderCount += item.quantity.toLong()
                            salesAmount += item.amount
                            score += RankingScorePolicy.orderItemScore(item.amount)
                        }
                    }
                }
                else -> log.warn("알 수 없는 이벤트 타입: eventType={}", event.eventType)
            }
        }

        aggregated.forEach { (key, delta) ->
            metricsRepository.upsert(
                key.productId,
                key.date,
                delta.viewCount,
                delta.likeCount,
                delta.orderCount,
                delta.salesAmount,
                delta.score,
            )
        }

        eventHandledRepository.saveAll(
            newEvents.map { RankingEventHandledJpaEntity(it.eventId, it.eventType) },
        )
    }

    private fun collectAffectedKeys(events: List<IncomingEvent>): Set<DailyProductKey> {
        val keys = mutableSetOf<DailyProductKey>()

        for (event in events) {
            val date = eventDate(event)
            when (event.eventType) {
                LIKE_INCREASED, LIKE_DECREASED, PRODUCT_VIEWED -> {
                    val payload = objectMapper.convertValue(event.payload, ProductMetricPayload::class.java)
                    keys += DailyProductKey(payload.productId, date)
                }
                PAYMENT_SUCCESS -> {
                    val payload = objectMapper.convertValue(event.payload, OrderMetricPayload::class.java)
                    payload.items.forEach { item -> keys += DailyProductKey(item.productId, date) }
                }
                else -> Unit
            }
        }

        return keys
    }

    private fun eventDate(event: IncomingEvent): LocalDate =
        try {
            ZonedDateTime.parse(event.occurredAt)
                .withZoneSameInstant(ZONE)
                .toLocalDate()
        } catch (exception: DateTimeParseException) {
            throw IllegalArgumentException(
                "occurredAt 형식이 올바르지 않습니다. eventId=${event.eventId}, occurredAt=${event.occurredAt}",
                exception,
            )
        }

    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")
        private const val LIKE_INCREASED = "ProductLikeMetricIncreased"
        private const val LIKE_DECREASED = "ProductLikeMetricDecreased"
        private const val PAYMENT_SUCCESS = "PAYMENT_SUCCESS"
        private const val PRODUCT_VIEWED = "PRODUCT_VIEWED"
    }
}

private data class MetricsDelta(
    var viewCount: Long = 0,
    var likeCount: Long = 0,
    var orderCount: Long = 0,
    var salesAmount: Long = 0,
    var score: Double = 0.0,
)
