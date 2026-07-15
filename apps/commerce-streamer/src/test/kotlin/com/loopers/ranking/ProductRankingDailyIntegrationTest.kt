package com.loopers.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.metrics.application.ProductMetricsService
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
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    private fun handle(eventId: String, eventType: String, json: String, occurredAt: Instant = NOON_KST) {
        productMetricsService.handle(eventId, eventType, objectMapper.readTree(json), occurredAt)
    }

    private fun score(date: LocalDate, productId: Long): BigDecimal? =
        productRankingDailyJpaRepository.findAll()
            .firstOrNull { it.rankingDate == date && it.productId == productId }
            ?.score

    @DisplayName("조회 이벤트를 처리하면, 오늘 판에 +0.1 과 내일 판에 +0.01 을 함께 적재한다 — dual write.")
    @Test
    fun accumulatesViewScoreToTodayAndCarryToTomorrow() {
        handle("e-1", "ProductViewedEvent", """{"productId":1}""")

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.1") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.01") },
            { assertThat(productRankingDailyJpaRepository.count()).isEqualTo(2L) },
        )
    }

    @DisplayName("좋아요 이벤트를 처리하면, 오늘 판에 +0.2 와 내일 판에 +0.02 를 적재한다.")
    @Test
    fun accumulatesLikeScore() {
        handle("e-1", "ProductLikedEvent", """{"productId":1}""")

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.02") },
        )
    }

    @DisplayName("좋아요 취소 이벤트가 단독으로 오면, 오늘 판 -0.2 와 내일 판 -0.02 로 음수 score 행을 허용한다.")
    @Test
    fun accumulatesNegativeScore_whenUnlikedAlone() {
        handle("e-1", "ProductUnlikedEvent", """{"productId":1}""")

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("-0.2") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("-0.02") },
        )
    }

    @DisplayName("주문 이벤트는 수량·단가와 무관하게 order line 당 +0.7 을 각 상품에 적재한다.")
    @Test
    fun accumulatesFixedOrderScorePerOrderLine_ignoringQuantityAndPrice() {
        handle(
            "e-1",
            "OrderCreatedEvent",
            """{"orderId":10,"items":[
                {"productId":1,"quantity":100,"unitPrice":10},
                {"productId":2,"quantity":1,"unitPrice":1000000}
            ]}""",
        )

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
        handle("e-1", "ProductViewedEvent", """{"productId":1}""")
        handle("e-2", "ProductViewedEvent", """{"productId":1}""")

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2") },
            { assertThat(score(TOMORROW, 1)).isEqualByComparingTo("0.02") },
            { assertThat(productRankingDailyJpaRepository.count()).isEqualTo(2L) },
        )
    }

    @DisplayName("같은 eventId 를 다시 처리하면, 랭킹 점수도 반복 적재하지 않는다 — event_handled 멱등 상속.")
    @Test
    fun skipsRankingAccumulation_whenDuplicateEventId() {
        handle("e-1", "ProductLikedEvent", """{"productId":1}""")
        handle("e-1", "ProductLikedEvent", """{"productId":1}""")

        assertAll(
            { assertThat(score(TODAY, 1)).isEqualByComparingTo("0.2") },
            { assertThat(productRankingDailyJpaRepository.count()).isEqualTo(2L) },
        )
    }

    @DisplayName("주문 1건(0.7) 이 좋아요 3건(0.6) 보다 높은 점수를 가진다 — 가중치 요건.")
    @Test
    fun singleOrderOutweighsThreeLikes() {
        handle("e-1", "OrderCreatedEvent", """{"orderId":10,"items":[{"productId":1,"quantity":1,"unitPrice":100}]}""")
        handle("e-2", "ProductLikedEvent", """{"productId":2}""")
        handle("e-3", "ProductLikedEvent", """{"productId":2}""")
        handle("e-4", "ProductLikedEvent", """{"productId":2}""")

        assertThat(score(TODAY, 1)).isGreaterThan(score(TODAY, 2))
    }

    @DisplayName("발생 시각을 KST 로 환산해 판을 가른다 — UTC 14:59 는 오늘, UTC 15:00 은 내일 날짜 판이다.")
    @Test
    fun splitsRankingDateByKst() {
        handle("e-1", "ProductViewedEvent", """{"productId":1}""", Instant.parse("2026-07-15T14:59:00Z"))
        handle("e-2", "ProductViewedEvent", """{"productId":2}""", Instant.parse("2026-07-15T15:00:00Z"))

        assertAll(
            { assertThat(score(LocalDate.of(2026, 7, 15), 1)).isEqualByComparingTo("0.1") },
            { assertThat(score(LocalDate.of(2026, 7, 16), 1)).isEqualByComparingTo("0.01") },
            { assertThat(score(LocalDate.of(2026, 7, 16), 2)).isEqualByComparingTo("0.1") },
            { assertThat(score(LocalDate.of(2026, 7, 17), 2)).isEqualByComparingTo("0.01") },
        )
    }

    @DisplayName("productId 없는 payload 나 알 수 없는 eventType 은 랭킹에도 기록하지 않는다.")
    @Test
    fun skipsRanking_whenPayloadInvalidOrEventTypeUnknown() {
        handle("e-1", "ProductLikedEvent", """{"other":1}""")
        handle("e-2", "UnknownEvent", """{"productId":1}""")

        assertThat(productRankingDailyJpaRepository.count()).isEqualTo(0L)
    }

    private companion object {
        val NOON_KST: Instant = Instant.parse("2026-07-15T03:00:00Z")
        val TODAY: LocalDate = LocalDate.of(2026, 7, 15)
        val TOMORROW: LocalDate = LocalDate.of(2026, 7, 16)
    }
}
