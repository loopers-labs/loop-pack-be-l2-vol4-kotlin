package com.loopers.ranking

import com.loopers.metrics.domain.EventHandledRepository
import com.loopers.metrics.domain.EventSubscription
import com.loopers.ranking.application.FallbackItem
import com.loopers.ranking.application.RankingFallbackService
import com.loopers.ranking.domain.RankingWeights
import com.loopers.ranking.domain.ScoreChange
import com.loopers.ranking.infrastructure.RankingFallbackDailyJpaRepository
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
import java.time.ZoneId

@SpringBootTest
@ActiveProfiles("test")
class RankingFallbackServiceIntegrationTest @Autowired constructor(
    private val rankingFallbackService: RankingFallbackService,
    private val rankingFallbackDailyJpaRepository: RankingFallbackDailyJpaRepository,
    private val eventHandledRepository: EventHandledRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    private fun item(eventId: String, vararg changes: ScoreChange) = FallbackItem(eventId, NOON, changes.toList())

    private fun score(date: LocalDate, productId: Long): BigDecimal? =
        rankingFallbackDailyJpaRepository.findByRankingDateAndProductId(date, productId)?.score

    @DisplayName("배치를 적용하면 오늘 판에 +변화량, 내일 판에 +변화량x0.1 을 적재한다.")
    @Test
    fun appliesBatchToTodayAndCarriesToTomorrow() {
        rankingFallbackService.applyBatch(listOf(item("e-1", ScoreChange(1, RankingWeights.ORDER_LINE))))

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.7") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.07") },
        )
    }

    @DisplayName("같은 배치 안의 같은 상품 변화량은 합산되어 upsert 1회로 적재된다.")
    @Test
    fun sumsDeltasPerProductWithinBatch() {
        rankingFallbackService.applyBatch(
            listOf(
                item("e-1", ScoreChange(1, RankingWeights.VIEW)),
                item("e-2", ScoreChange(1, RankingWeights.LIKE)),
            ),
        )

        assertThat(score(TODAY, 1)).isEqualByComparingTo("0.3")
    }

    @DisplayName("이미 처리한 eventId 가 배치에 다시 오면 점수를 이중 가산하지 않는다 — inbox 멱등.")
    @Test
    fun skipsAlreadyHandledEvents_whenBatchRedelivered() {
        rankingFallbackService.applyBatch(listOf(item("e-1", ScoreChange(1, RankingWeights.LIKE))))
        rankingFallbackService.applyBatch(
            listOf(
                item("e-1", ScoreChange(1, RankingWeights.LIKE)),
                item("e-2", ScoreChange(1, RankingWeights.VIEW)),
            ),
        )

        assertThat(score(TODAY, 1)).isEqualByComparingTo("0.3")
    }

    @DisplayName("RANKING 구독이 처리한 eventId 라도 fallback 구독은 독립적으로 적재한다 — 구독별 멱등 분리.")
    @Test
    fun accumulatesIndependentlyFromRankingSubscription() {
        eventHandledRepository.markHandled("e-1", EventSubscription.RANKING)

        rankingFallbackService.applyBatch(listOf(item("e-1", ScoreChange(1, RankingWeights.LIKE))))

        assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2")
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 15)
        val TOMORROW: LocalDate = LocalDate.of(2026, 7, 16)
        val NOON: Instant = TODAY.atTime(12, 0).atZone(KST).toInstant()
    }
}
