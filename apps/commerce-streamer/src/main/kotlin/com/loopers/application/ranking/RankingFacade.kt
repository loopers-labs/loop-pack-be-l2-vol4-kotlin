package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScorePolicy
import com.loopers.domain.ranking.RankingSignal
import com.loopers.domain.ranking.RankingWeights
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

/**
 * 행동 신호를 랭킹 점수 반영으로 조율한다 — 발생 시각으로 랭킹판 키를, 신호·수량으로 가중 점수를 정해 저장소에 넘긴다.
 * 가중치·보존 기간은 설정(RankingProperties)으로 외부화한다. 순수 정책(RankingScorePolicy)은 빈이 아니라 여기서 직접 만든다.
 */
@Component
class RankingFacade(
    private val rankingRepository: RankingRepository,
    properties: RankingProperties,
) {
    private val scorePolicy = RankingScorePolicy(
        RankingWeights(properties.weight.view, properties.weight.like, properties.weight.order),
    )
    private val ttlSeconds = properties.keyTtlHours * SECONDS_PER_HOUR

    fun reflect(eventId: UUID, signal: RankingSignal, productId: Long, quantity: Int, occurredAt: LocalDateTime) {
        val delta = scorePolicy.scoreOf(signal, quantity)
        rankingRepository.incrementScoreOnce(eventId, RankingKey.of(occurredAt), productId, delta, ttlSeconds)
    }

    companion object {
        private const val SECONDS_PER_HOUR = 3_600L
    }
}
