package com.loopers.projection.ranking.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RankingScorePolicyTest {
    @Test
    fun `좋아요_증가는_건당_플러스_점수를_반환한다`() {
        assertThat(policy.likeScore(1)).isEqualTo(1.0)
    }

    @Test
    fun `좋아요_취소는_건당_마이너스_점수를_반환한다`() {
        assertThat(policy.likeScore(-1)).isEqualTo(-1.0)
    }

    @Test
    fun `결제_완료_주문은_상품당_고정_점수를_반환한다`() {
        assertThat(policy.orderScore()).isEqualTo(4.0)
    }

    companion object {
        private val policy = RankingScorePolicy(
            RankingProperties(
                score = RankingScoreProperties(like = 1.0, order = 4.0),
                carryOver = RankingCarryOverProperties(
                    enabled = true,
                    cron = "0 55 23 * * *",
                    decay = 0.5,
                    minScore = 1.0,
                ),
                rdbSync = RankingRdbSyncProperties(enabled = true, fixedDelayMs = 600_000, topN = 1000),
            ),
        )
    }
}
