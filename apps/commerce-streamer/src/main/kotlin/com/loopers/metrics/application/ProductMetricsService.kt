package com.loopers.metrics.application

import com.loopers.metrics.domain.EventHandled
import com.loopers.metrics.domain.EventHandledRepository
import com.loopers.metrics.domain.ProductMetricsRepository
import com.loopers.ranking.domain.ProductRankingDailyRepository
import com.loopers.ranking.domain.RankingWeights
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
    @Transactional
    fun handle(event: ProductEvent, occurredAt: Instant) = handleOnce(event.eventId) {
        when (event) {
            is ProductEvent.Liked -> {
                productMetricsRepository.upsertDelta(event.productId, likeDelta = 1)
                accumulateRanking(occurredAt, event.productId, RankingWeights.LIKE)
            }
            is ProductEvent.Unliked -> {
                productMetricsRepository.upsertDelta(event.productId, likeDelta = -1)
                accumulateRanking(occurredAt, event.productId, RankingWeights.LIKE.negate())
            }
        }
    }

    @Transactional
    fun handle(event: OrderCreatedEvent, occurredAt: Instant) = handleOnce(event.eventId) {
        event.items.forEach { line ->
            productMetricsRepository.upsertDelta(line.productId, salesDelta = line.quantity)
            accumulateRanking(occurredAt, line.productId, RankingWeights.ORDER_LINE)
        }
    }

    @Transactional
    fun handle(event: ProductViewedEvent, occurredAt: Instant) = handleOnce(event.eventId) {
        productMetricsRepository.upsertDelta(event.productId, viewDelta = 1)
        accumulateRanking(occurredAt, event.productId, RankingWeights.VIEW)
    }

    private inline fun handleOnce(eventId: String, aggregate: () -> Unit) {
        if (eventHandledRepository.exists(eventId)) {
            return
        }
        aggregate()
        eventHandledRepository.save(EventHandled(eventId))
    }

    private fun accumulateRanking(occurredAt: Instant, productId: Long, delta: BigDecimal) {
        val eventDate = occurredAt.atZone(KST).toLocalDate()
        productRankingDailyRepository.accumulate(eventDate, productId, delta)
        productRankingDailyRepository.accumulate(eventDate.plusDays(1), productId, delta.multiply(RankingWeights.CARRY_RATE))
    }

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
