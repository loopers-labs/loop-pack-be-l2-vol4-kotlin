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
    fun accumulate(eventId: String, occurredAt: Instant, changes: List<ScoreChange>) {
        if (eventHandledRepository.exists(eventId, EventSubscription.RANKING)) {
            return
        }
        val eventDate = occurredAt.atZone(KST).toLocalDate()
        changes.forEach { change ->
            productRankingDailyRepository.accumulate(eventDate, change.productId, change.amount)
            productRankingDailyRepository.accumulate(
                eventDate.plusDays(1),
                change.productId,
                change.amount.multiply(RankingWeights.CARRY_RATE),
            )
        }
        eventHandledRepository.markHandled(eventId, EventSubscription.RANKING)
    }

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}

data class ScoreChange(
    val productId: Long,
    val amount: BigDecimal,
)
