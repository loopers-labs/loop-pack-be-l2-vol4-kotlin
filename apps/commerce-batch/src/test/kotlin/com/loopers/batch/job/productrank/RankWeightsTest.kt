package com.loopers.batch.job.productrank

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class RankWeightsTest {

    private val weights = RankWeights(viewWeight = 10L, likeWeight = 50L, orderWeight = 500L)

    @DisplayName("타입별 가중치를 반환하고, 모르는 타입은 0으로 무시한다.")
    @Test
    fun mapsTypeToWeight() {
        assertThat(weights.weightFor("VIEW")).isEqualTo(10L)
        assertThat(weights.weightFor("LIKE")).isEqualTo(50L)
        assertThat(weights.weightFor("SALES")).isEqualTo(500L)
        assertThat(weights.weightFor("UNKNOWN")).isEqualTo(0L)
    }

    @DisplayName("점수는 view×w_v + like×w_l + sales×w_o 로 계산한다.")
    @Test
    fun calculatesWeightedScore() {
        val score = weights.scoreOf(viewCount = 100L, likeCount = 10L, salesCount = 2L)

        assertThat(score).isEqualTo(100L * 10 + 10L * 50 + 2L * 500) // 2500
    }
}
