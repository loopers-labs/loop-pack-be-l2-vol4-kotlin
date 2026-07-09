package com.loopers.domain.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class WaitingTimeEstimatorTest {
    @DisplayName("예상 대기 시간을 계산할 때,")
    @Nested
    inner class EstimateSeconds {
        @DisplayName("ceil(rank / batchSize) × interval(초) 로 계산한다.")
        @Test
        fun calculatesByStaticThroughput() {
            // batchSize 100, interval 1초
            val estimator = WaitingTimeEstimator(batchSize = 100, intervalMs = 1000)

            // ceil(1/100)=1 -> 1s, ceil(100/100)=1 -> 1s, ceil(101/100)=2 -> 2s, ceil(500/100)=5 -> 5s
            assertAll(
                { assertThat(estimator.estimateSeconds(1)).isEqualTo(1L) },
                { assertThat(estimator.estimateSeconds(100)).isEqualTo(1L) },
                { assertThat(estimator.estimateSeconds(101)).isEqualTo(2L) },
                { assertThat(estimator.estimateSeconds(500)).isEqualTo(5L) },
            )
        }

        @DisplayName("interval 이 1초가 아니어도 주기 수에 곱해 반영한다.")
        @Test
        fun reflectsInterval() {
            // batchSize 10, interval 2초. rank 25 -> ceil(25/10)=3 주기 -> 3 * 2s = 6s
            val estimator = WaitingTimeEstimator(batchSize = 10, intervalMs = 2000)

            assertThat(estimator.estimateSeconds(25)).isEqualTo(6L)
        }

        @DisplayName("batchSize 가 0 이면(입장 비활성) 0을 반환한다.")
        @Test
        fun returnsZero_whenBatchSizeIsZero() {
            val estimator = WaitingTimeEstimator(batchSize = 0, intervalMs = 1000)

            assertThat(estimator.estimateSeconds(500)).isEqualTo(0L)
        }
    }
}
