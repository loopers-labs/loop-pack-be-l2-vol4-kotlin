package com.loopers.projection.ranking.application

import com.loopers.projection.ranking.port.ProductRankingStore
import org.springframework.stereotype.Service

@Service
class RankingProjectionService(
    private val productRankingStore: ProductRankingStore,
) {
    fun project(command: RankingProjectionCommand) {
        val date = command.occurredAt.withZoneSameInstant(RankingKey.ZONE).toLocalDate()
        command.deltas.forEach { delta ->
            productRankingStore.incrementScore(
                date = date,
                eventId = command.eventId,
                productId = delta.productId,
                score = delta.score,
            )
        }
    }
}
