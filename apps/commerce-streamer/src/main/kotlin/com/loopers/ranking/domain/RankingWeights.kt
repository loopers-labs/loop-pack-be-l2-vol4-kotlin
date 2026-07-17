package com.loopers.ranking.domain

import com.loopers.shared.event.ConsumedEvent
import com.loopers.shared.event.OrderCreatedEvent
import com.loopers.shared.event.ProductEvent
import com.loopers.shared.event.ProductViewedEvent
import java.math.BigDecimal

object RankingWeights {
    val VIEW: BigDecimal = BigDecimal("0.1")
    val LIKE: BigDecimal = BigDecimal("0.2")
    val ORDER_LINE: BigDecimal = BigDecimal("0.7")
    val CARRY_RATE: BigDecimal = BigDecimal("0.1")

    fun changesOf(event: ConsumedEvent): List<ScoreChange> = when (event) {
        is ProductEvent.Liked -> listOf(ScoreChange(event.productId, LIKE))
        is ProductEvent.Unliked -> listOf(ScoreChange(event.productId, LIKE.negate()))
        is OrderCreatedEvent -> event.items.map { line -> ScoreChange(line.productId, ORDER_LINE) }
        is ProductViewedEvent -> listOf(ScoreChange(event.productId, VIEW))
        else -> error("랭킹 가중치가 정의되지 않은 이벤트 타입입니다: ${event::class.simpleName}")
    }
}
