package com.loopers.domain.ranking

/**
 * 행동 신호와 수량을 가중 점수로 환산한다. 무상태 순수 정책 — 인자만으로 계산한다.
 */
class RankingScorePolicy(
    private val weights: RankingWeights,
) {
    fun scoreOf(signal: RankingSignal, quantity: Int): Double = when (signal) {
        RankingSignal.VIEW -> weights.view * quantity
        RankingSignal.LIKE -> weights.like * quantity
        RankingSignal.LIKE_CANCEL -> -weights.like * quantity
        RankingSignal.ORDER -> weights.order * quantity
    }
}
