package com.loopers.ranking

import com.loopers.metrics.application.OrderCreatedEvent
import com.loopers.metrics.application.ProductEvent
import com.loopers.metrics.application.ProductMetricsService
import com.loopers.metrics.application.ProductViewedEvent
import com.loopers.ranking.infrastructure.ProductRankingDailyJpaRepository
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
    private val productMetricsService: ProductMetricsService,
    private val productRankingDailyJpaRepository: ProductRankingDailyJpaRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    private fun like(eventId: String, productId: Long, occurredAt: Instant = NOON_KST) =
        productMetricsService.handle(ProductEvent.Liked(eventId, productId), occurredAt)

    private fun unlike(eventId: String, productId: Long, occurredAt: Instant = NOON_KST) =
        productMetricsService.handle(ProductEvent.Unliked(eventId, productId), occurredAt)

    private fun view(eventId: String, productId: Long, occurredAt: Instant = NOON_KST) =
        productMetricsService.handle(ProductViewedEvent(eventId, productId), occurredAt)

    private fun order(eventId: String, vararg items: OrderCreatedEvent.OrderLine) =
        productMetricsService.handle(OrderCreatedEvent(eventId, items.toList()), NOON_KST)

    private fun score(date: LocalDate, productId: Long): BigDecimal? =
        productRankingDailyJpaRepository.findAll()
            .firstOrNull { it.rankingDate == date && it.productId == productId }
            ?.score

    @DisplayName("조회 이벤트를 처리하면, 오늘 판에 +0.1 과 내일 판에 +0.01 을 함께 적재한다 — dual write.")
    @Test
    fun accumulatesViewScoreToTodayAndCarryToTomorrow() {
        view("e-1", 1)

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.1") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.01") },
            { assertThat(productRankingDailyJpaRepository.count()).isEqualTo(2L) },
        )
    }

    @DisplayName("좋아요 이벤트를 처리하면, 오늘 판에 +0.2 와 내일 판에 +0.02 를 적재한다.")
    @Test
    fun accumulatesLikeScore() {
        like("e-1", 1)

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.02") },
        )
    }

    @DisplayName("좋아요 취소 이벤트가 단독으로 오면, 오늘 판 -0.2 와 내일 판 -0.02 로 음수 score 행을 허용한다.")
    @Test
    fun accumulatesNegativeScore_whenUnlikedAlone() {
        unlike("e-1", 1)

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("-0.2") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("-0.02") },
        )
    }

    @DisplayName("주문 이벤트는 수량·단가와 무관하게 order line 당 +0.7 을 각 상품에 적재한다.")
    @Test
    fun accumulatesFixedOrderScorePerOrderLine_ignoringQuantityAndPrice() {
        order("e-1", OrderCreatedEvent.OrderLine(1, 100), OrderCreatedEvent.OrderLine(2, 1))

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
        view("e-1", 1)
        view("e-2", 1)

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.02") },
            { assertThat(productRankingDailyJpaRepository.count()).isEqualTo(2L) },
        )
    }

    @DisplayName("같은 eventId 를 다시 처리하면, 랭킹 점수도 반복 적재하지 않는다 — event_handled 멱등 상속.")
    @Test
    fun skipsRankingAccumulation_whenDuplicateEventId() {
        like("e-1", 1)
        like("e-1", 1)

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2") },
            { assertThat(productRankingDailyJpaRepository.count()).isEqualTo(2L) },
        )
    }

    @DisplayName("주문 1건(0.7) 이 좋아요 3건(0.6) 보다 높은 점수를 가진다 — 가중치 요건.")
    @Test
    fun singleOrderOutweighsThreeLikes() {
        order("e-1", OrderCreatedEvent.OrderLine(1, 1))
        like("e-2", 2)
        like("e-3", 2)
        like("e-4", 2)

        assertThat(score(TODAY, 1)).isGreaterThan(score(TODAY, 2))
    }

    @DisplayName("발생 시각을 KST 로 환산해 판을 가른다 — UTC 14:59 는 오늘, UTC 15:00 은 내일 날짜 판이다.")
    @Test
    fun splitsRankingDateByKst() {
        view("e-1", 1, Instant.parse("2026-07-15T14:59:00Z"))
        view("e-2", 2, Instant.parse("2026-07-15T15:00:00Z"))

        assertAll(
            { assertThat(score(LocalDate.of(2026, 7, 15), 1)).isEqualByComparingTo("0.1") },
            { assertThat(score(LocalDate.of(2026, 7, 16), 1)).isEqualByComparingTo("0.01") },
            { assertThat(score(LocalDate.of(2026, 7, 16), 2)).isEqualByComparingTo("0.1") },
            { assertThat(score(LocalDate.of(2026, 7, 17), 2)).isEqualByComparingTo("0.01") },
        )
    }

    private companion object {
        val NOON_KST: Instant = Instant.parse("2026-07-15T03:00:00Z")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 15)
        val TOMORROW: LocalDate = LocalDate.of(2026, 7, 16)
    }
}
