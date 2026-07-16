package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScorePolicy
import com.loopers.domain.ranking.RankingSignal
import com.loopers.domain.ranking.RankingWeights
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.ceil

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

    // 보존 기간으로부터 살아 있을 수 있는 일간 판 수를 도출한다 — TTL 이 바뀌어도 삭제 정리가 따라간다.
    private val retentionDays = ceil(properties.keyTtlHours / HOURS_PER_DAY).toInt()

    fun reflect(eventId: UUID, signal: RankingSignal, productId: Long, quantity: Int, occurredAt: LocalDateTime) {
        val delta = scorePolicy.scoreOf(signal, quantity)
        rankingRepository.incrementScoreOnce(eventId, RankingKey.of(occurredAt), productId, delta, ttlSeconds)
    }

    /**
     * 삭제된 상품을 살아 있을 수 있는 모든 일간 랭킹판에서 걷어낸다.
     * 판 범위는 보존 기간(ttl)에서 도출하고, 이월로 미리 생성된 내일 판까지 포함한다 — 특정 TTL 값에 하드코딩되지 않는다.
     */
    fun removeProduct(productId: Long, occurredAt: LocalDateTime) {
        val today = occurredAt.toLocalDate()
        val keys = (CARRY_OVER_LOOKAHEAD_DAYS downTo -retentionDays)
            .map { RankingKey.of(today.plusDays(it.toLong())) }
        rankingRepository.removeProduct(keys, productId)
    }

    companion object {
        private const val SECONDS_PER_HOUR = 3_600L
        private const val HOURS_PER_DAY = 24.0
        private const val CARRY_OVER_LOOKAHEAD_DAYS = 1
    }
}
