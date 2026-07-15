package com.loopers.metrics.application

import com.fasterxml.jackson.databind.JsonNode
import com.loopers.metrics.domain.EventHandled
import com.loopers.metrics.domain.EventHandledRepository
import com.loopers.metrics.domain.ProductMetricsRepository
import com.loopers.ranking.domain.ProductRankingDailyRepository
import com.loopers.ranking.domain.RankingWeights
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

@Service
class ProductMetricsService(
    private val eventHandledRepository: EventHandledRepository,
    private val productMetricsRepository: ProductMetricsRepository,
    private val productRankingDailyRepository: ProductRankingDailyRepository,
) {
    private val logger = LoggerFactory.getLogger(ProductMetricsService::class.java)

    @Transactional
    fun handle(eventId: String, eventType: String, payload: JsonNode, occurredAt: Instant) {
        if (eventHandledRepository.exists(eventId)) {
            return
        }
        when (eventType) {
            "ProductLikedEvent" -> {
                val productId = requiredLong(payload, "productId", eventId) ?: return
                productMetricsRepository.upsertDelta(productId, likeDelta = 1)
                accumulateRanking(occurredAt, productId, RankingWeights.LIKE)
            }
            "ProductUnlikedEvent" -> {
                val productId = requiredLong(payload, "productId", eventId) ?: return
                productMetricsRepository.upsertDelta(productId, likeDelta = -1)
                accumulateRanking(occurredAt, productId, RankingWeights.LIKE.negate())
            }
            "ProductViewedEvent" -> {
                val productId = requiredLong(payload, "productId", eventId) ?: return
                productMetricsRepository.upsertDelta(productId, viewDelta = 1)
                accumulateRanking(occurredAt, productId, RankingWeights.VIEW)
            }
            "OrderCreatedEvent" -> (payload["items"] ?: return skipMissingField("items", eventId)).forEach {
                val productId = it["productId"].asLong()
                productMetricsRepository.upsertDelta(productId, salesDelta = it["quantity"].asLong())
                accumulateRanking(occurredAt, productId, RankingWeights.ORDER_LINE)
            }
            else -> {
                logger.warn("알 수 없는 eventType — skip (eventType={}, eventId={})", eventType, eventId)
                return
            }
        }
        eventHandledRepository.save(EventHandled(eventId))
    }

    private fun accumulateRanking(occurredAt: Instant, productId: Long, delta: BigDecimal) {
        val eventDate = occurredAt.atZone(KST).toLocalDate()
        productRankingDailyRepository.accumulate(eventDate, productId, delta)
        productRankingDailyRepository.accumulate(eventDate.plusDays(1), productId, delta.multiply(RankingWeights.CARRY_RATE))
    }

    // 프로듀서 계약상 항상 존재하는 필드 — 누락은 이상 payload 신호이므로 NPE 로 배치 전체를 재전달시키지 않고 warn + skip 만 한다.
    private fun requiredLong(payload: JsonNode, field: String, eventId: String): Long? {
        val node = payload[field]
        if (node == null) {
            skipMissingField(field, eventId)
            return null
        }
        return node.asLong()
    }

    private fun skipMissingField(field: String, eventId: String) {
        logger.warn("{} 없는 payload — skip (eventId={})", field, eventId)
    }

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
