package com.loopers.ranking.application

import com.loopers.metrics.domain.EventHandledRepository
import com.loopers.metrics.domain.EventSubscription
import com.loopers.ranking.domain.ProductRankingDailyRepository
import com.loopers.ranking.domain.RankingWeights
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

@Service
class RankingAccumulateService(
    private val eventHandledRepository: EventHandledRepository,
    private val productRankingDailyRepository: ProductRankingDailyRepository,
) {
    @Transactional
    fun accumulate(eventId: String, occurredAt: Instant, deltas: List<ScoreDelta>) {
        if (eventHandledRepository.exists(eventId, EventSubscription.RANKING)) {
            return
        }
        val eventDate = occurredAt.atZone(KST).toLocalDate()
        deltas.forEach { delta ->
            productRankingDailyRepository.accumulate(eventDate, delta.productId, delta.amount)
            productRankingDailyRepository.accumulate(
                eventDate.plusDays(1),
                delta.productId,
                delta.amount.multiply(RankingWeights.CARRY_RATE),
            )
        }
        eventHandledRepository.markHandled(eventId, EventSubscription.RANKING)
    }

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}

data class ScoreDelta(
    val productId: Long,
    val amount: BigDecimal,
)
