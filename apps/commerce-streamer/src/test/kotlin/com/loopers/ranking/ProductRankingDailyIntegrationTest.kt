package com.loopers.ranking

import com.loopers.metrics.application.ProductMetricsService
import com.loopers.ranking.application.RankingAccumulateService
import com.loopers.ranking.application.ScoreDelta
import com.loopers.ranking.domain.RankingWeights
import com.loopers.ranking.infrastructure.ProductRankingDailyJpaRepository
import com.loopers.shared.event.ProductEvent
import com.loopers.support.DatabaseCleanup
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
class ProductRankingDailyIntegrationTest @Autowired constructor(
    private val rankingAccumulateService: RankingAccumulateService,
    private val productMetricsService: ProductMetricsService,
    private val productRankingDailyJpaRepository: ProductRankingDailyJpaRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    private fun accumulate(eventId: String, vararg deltas: ScoreDelta, occurredAt: Instant = NOON_KST) =
        rankingAccumulateService.accumulate(eventId, occurredAt, deltas.toList())

    private fun score(date: LocalDate, productId: Long): BigDecimal? =
        productRankingDailyJpaRepository.findAll()
            .firstOrNull { it.rankingDate == date && it.productId == productId }
            ?.score

    @DisplayName("조회 델타(+0.1)를 적재하면, 오늘 판에 +0.1 과 내일 판에 +0.01 을 함께 적재한다 — dual write.")
    @Test
    fun accumulatesViewScoreToTodayAndCarryToTomorrow() {
        accumulate("e-1", ScoreDelta(1, RankingWeights.VIEW))

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.1") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.01") },
            { assertThat(productRankingDailyJpaRepository.count()).isEqualTo(2L) },
        )
    }

    @DisplayName("좋아요 델타(+0.2)를 적재하면, 오늘 판에 +0.2 와 내일 판에 +0.02 를 적재한다.")
    @Test
    fun accumulatesLikeScore() {
        accumulate("e-1", ScoreDelta(1, RankingWeights.LIKE))

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.02") },
        )
    }

    @DisplayName("좋아요 취소 델타(-0.2)가 단독으로 오면, 오늘 판 -0.2 와 내일 판 -0.02 로 음수 score 행을 허용한다.")
    @Test
    fun accumulatesNegativeScore_whenUnlikedAlone() {
        accumulate("e-1", ScoreDelta(1, RankingWeights.LIKE.negate()))

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("-0.2") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("-0.02") },
        )
    }

    @DisplayName("한 이벤트의 델타 목록은 각 상품에 개별 적재된다 — 주문 order line 당 +0.7.")
    @Test
    fun accumulatesEachDeltaOfSingleEvent() {
        accumulate("e-1", ScoreDelta(1, RankingWeights.ORDER_LINE), ScoreDelta(2, RankingWeights.ORDER_LINE))

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.7") },
            { assertThat(score(TODAY, 2)).isEqualByComparingTo("0.7") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.07") },
            { assertThat(score(TOMORROW, 2)).isEqualByComparingTo("0.07") },
        )
    }

    @DisplayName("같은 상품에 이벤트가 반복되면, 행을 늘리지 않고 score 를 누적한다 — upsert 경로.")
    @Test
    fun accumulatesScoreOnSameRow_whenEventsRepeat() {
        accumulate("e-1", ScoreDelta(1, RankingWeights.VIEW))
        accumulate("e-2", ScoreDelta(1, RankingWeights.VIEW))

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.02") },
            { assertThat(productRankingDailyJpaRepository.count()).isEqualTo(2L) },
        )
    }

    @DisplayName("같은 eventId 를 다시 처리하면, 랭킹 점수를 반복 적재하지 않는다 — RANKING 구독 멱등.")
    @Test
    fun skipsRankingAccumulation_whenDuplicateEventId() {
        accumulate("e-1", ScoreDelta(1, RankingWeights.LIKE))
        accumulate("e-1", ScoreDelta(1, RankingWeights.LIKE))

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2") },
            { assertThat(productRankingDailyJpaRepository.count()).isEqualTo(2L) },
        )
    }

    @DisplayName("주문 1건(0.7) 이 좋아요 3건(0.6) 보다 높은 점수를 가진다 — 가중치 요건.")
    @Test
    fun singleOrderOutweighsThreeLikes() {
        accumulate("e-1", ScoreDelta(1, RankingWeights.ORDER_LINE))
        accumulate("e-2", ScoreDelta(2, RankingWeights.LIKE))
        accumulate("e-3", ScoreDelta(2, RankingWeights.LIKE))
        accumulate("e-4", ScoreDelta(2, RankingWeights.LIKE))

        assertThat(score(TODAY, 1)).isGreaterThan(score(TODAY, 2))
    }

    @DisplayName("발생 시각을 KST 로 환산해 판을 가른다 — UTC 14:59 는 오늘, UTC 15:00 은 내일 날짜 판이다.")
    @Test
    fun splitsRankingDateByKst() {
        accumulate("e-1", ScoreDelta(1, RankingWeights.VIEW), occurredAt = Instant.parse("2026-07-15T14:59:00Z"))
        accumulate("e-2", ScoreDelta(2, RankingWeights.VIEW), occurredAt = Instant.parse("2026-07-15T15:00:00Z"))

        assertAll(
            { assertThat(score(LocalDate.of(2026, 7, 15), 1)).isEqualByComparingTo("0.1") },
            { assertThat(score(LocalDate.of(2026, 7, 16), 1)).isEqualByComparingTo("0.01") },
            { assertThat(score(LocalDate.of(2026, 7, 16), 2)).isEqualByComparingTo("0.1") },
            { assertThat(score(LocalDate.of(2026, 7, 17), 2)).isEqualByComparingTo("0.01") },
        )
    }

    @DisplayName("METRICS 구독이 처리한 eventId 라도 RANKING 구독은 독립적으로 적재한다 — 구독별 멱등 기록.")
    @Test
    fun accumulatesIndependently_whenSameEventIdHandledByMetricsSubscription() {
        productMetricsService.handle(ProductEvent.Liked("e-1", 1))

        accumulate("e-1", ScoreDelta(1, RankingWeights.LIKE))

        assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2")
    }

    private companion object {
        val NOON_KST: Instant = Instant.parse("2026-07-15T03:00:00Z")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 15)
        val TOMORROW: LocalDate = LocalDate.of(2026, 7, 16)
    }
}
