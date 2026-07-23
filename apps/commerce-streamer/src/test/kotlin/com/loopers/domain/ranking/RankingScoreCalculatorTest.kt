package com.loopers.domain.ranking

import com.loopers.domain.metrics.ProductMetricsModel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RankingScoreCalculatorTest {

    private val date = LocalDate.of(2026, 7, 15)

    private fun metric(id: Long, view: Int, like: Int, sales: Int) =
        ProductMetricsModel(id = id, likeCount = like, salesCount = sales, viewCount = view, version = 0L)

    private fun baseline(id: Long, view: Int, like: Int, sales: Int) =
        ProductRankingBaseline(productId = id, baselineDate = date, viewCount = view, likeCount = like, salesCount = sales)

    @DisplayName("현재 누적에서 baseline 을 뺀 오늘치에 가중치를 적용해 점수를 낸다")
    @Test
    fun computesDailyDeltaWithWeights() {
        val metrics = listOf(metric(id = 1L, view = 10, like = 5, sales = 2))
        val baselines = mapOf(1L to baseline(id = 1L, view = 4, like = 1, sales = 0))

        val scores = RankingScoreCalculator.dailyScores(metrics, baselines)

        assertThat(scores[1L]).isCloseTo(2.8, within(1e-9))
    }

    @DisplayName("baseline 이 없는 상품은 0 기준으로 계산한다")
    @Test
    fun treatsMissingBaselineAsZero() {
        val metrics = listOf(metric(id = 2L, view = 3, like = 0, sales = 1))

        val scores = RankingScoreCalculator.dailyScores(metrics, emptyMap())

        assertThat(scores[2L]).isCloseTo(1.0, within(1e-9))
    }

    @DisplayName("오늘 활동이 없거나 순감소해 점수가 0 이하인 상품은 제외한다")
    @Test
    fun excludesNonPositiveScores() {
        val metrics = listOf(
            metric(id = 3L, view = 10, like = 2, sales = 1),
            metric(id = 4L, view = 5, like = 0, sales = 0),
        )
        val baselines = mapOf(
            3L to baseline(id = 3L, view = 10, like = 2, sales = 1),
            4L to baseline(id = 4L, view = 5, like = 3, sales = 0),
        )

        val scores = RankingScoreCalculator.dailyScores(metrics, baselines)

        assertThat(scores).doesNotContainKey(3L)
        assertThat(scores).doesNotContainKey(4L)
    }
}