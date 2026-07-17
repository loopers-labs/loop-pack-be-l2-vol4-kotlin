package com.loopers.ranking.application

import com.loopers.metrics.domain.EventHandledRepository
import com.loopers.metrics.domain.EventSubscription
import com.loopers.ranking.domain.RankingKeys
import com.loopers.ranking.domain.RankingWeights
import com.loopers.ranking.domain.ScoreChange
import com.loopers.ranking.infrastructure.RankingFallbackDailyJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@Service
class RankingFallbackService(
    private val eventHandledRepository: EventHandledRepository,
    private val rankingFallbackDailyJpaRepository: RankingFallbackDailyJpaRepository,
) {
    @Transactional
    fun applyBatch(items: List<FallbackItem>) {
        val fresh = items.filter { !eventHandledRepository.exists(it.eventId, EventSubscription.RANKING_FALLBACK) }
        if (fresh.isEmpty()) {
            return
        }
        fresh.flatMap { item -> item.changes.map { change -> item.eventDate() to change } }
            .groupBy { (date, change) -> date to change.productId }
            .forEach { (key, grouped) ->
                val (date, productId) = key
                val delta = grouped.sumOf { (_, change) -> change.amount }
                rankingFallbackDailyJpaRepository.upsertChange(date, productId, delta)
                rankingFallbackDailyJpaRepository.upsertChange(
                    date.plusDays(1),
                    productId,
                    delta.multiply(RankingWeights.CARRY_RATE),
                )
            }
        fresh.forEach { eventHandledRepository.markHandled(it.eventId, EventSubscription.RANKING_FALLBACK) }
    }
}

data class FallbackItem(
    val eventId: String,
    val occurredAt: Instant,
    val changes: List<ScoreChange>,
) {
    fun eventDate(): LocalDate = occurredAt.atZone(RankingKeys.KST).toLocalDate()
}
