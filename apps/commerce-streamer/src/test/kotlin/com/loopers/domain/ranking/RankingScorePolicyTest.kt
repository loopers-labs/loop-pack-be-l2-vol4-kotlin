package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.math.log10

class RankingScorePolicyTest {
    private val policy = RankingScorePolicy(viewWeight = 0.1, likeWeight = 0.2, orderWeight = 0.6)

    @DisplayName("조회는 +0.1, 좋아요는 +0.2, 좋아요 취소는 -0.2 점수를 만든다.")
    @Test
    fun basicScores() {
        assertThat(policy.viewed()).isEqualTo(0.1)
        assertThat(policy.likeAdded()).isEqualTo(0.2)
        assertThat(policy.likeRemoved()).isEqualTo(-0.2)
    }

    @DisplayName("주문 점수는 0.6 × log10(1 + 단가×수량)이다.")
    @Test
    fun orderScoreIsLogNormalized() {
        val score = policy.ordered(unitPrice = BigDecimal("30000"), quantity = 1)
        assertThat(score).isCloseTo(0.6 * log10(1.0 + 30000.0), org.assertj.core.data.Offset.offset(1e-9))
    }

    @DisplayName("주문 1건(3만원)이 좋아요 3건보다 점수가 높다 — 체크리스트 검증.")
    @Test
    fun oneOrderBeatsThreeLikes() {
        val oneOrder = policy.ordered(unitPrice = BigDecimal("30000"), quantity = 1)
        val threeLikes = policy.likeAdded() * 3
        assertThat(oneOrder).isGreaterThan(threeLikes)
    }

    @DisplayName("단가 0원 주문은 0점이다 (log10(1)=0).")
    @Test
    fun zeroPriceOrderScoresZero() {
        assertThat(policy.ordered(unitPrice = BigDecimal.ZERO, quantity = 5)).isEqualTo(0.0)
    }
}
