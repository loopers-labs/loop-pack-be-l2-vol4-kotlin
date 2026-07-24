package com.loopers.application.ranking

import com.loopers.application.metrics.IncomingEvent
import com.loopers.infrastructure.ranking.DailyProductKey
import com.loopers.infrastructure.ranking.DailyProductRankingMetricsId
import com.loopers.infrastructure.ranking.DailyProductRankingMetricsJpaRepository
import com.loopers.infrastructure.ranking.RankingEventHandledJpaRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ln

@SpringBootTest
@EnableAutoConfiguration(exclude = [KafkaAutoConfiguration::class])
@Import(RankingEventProcessorTest.KafkaTestConfig::class)
class RankingEventProcessorTest @Autowired constructor(
    private val processor: RankingEventProcessor,
    private val metricsRepository: DailyProductRankingMetricsJpaRepository,
    private val eventHandledRepository: RankingEventHandledJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @TestConfiguration
    class KafkaTestConfig {
        @Bean
        fun kafkaProperties(): KafkaProperties = KafkaProperties()
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Test
    fun `조회 이벤트는 view_count 1 증가, 스코어 0_1 증가`() {
        val scores = processor.process(listOf(viewEvent(eventId = "evt-view-1", productId = 100L)))

        val metrics = findMetrics(100L, EVENT_DATE)
        assertThat(metrics.viewCount).isEqualTo(1)
        assertThat(metrics.rankingScore).isCloseTo(0.1, within(1e-9))
        assertThat(scores[DailyProductKey(100L, EVENT_DATE)]).isCloseTo(0.1, within(1e-9))
    }

    @Test
    fun `좋아요 증가 이벤트는 like_count 1 증가, 스코어 0_2 증가`() {
        val scores = processor.process(listOf(likeIncreasedEvent(eventId = "evt-like-inc-1", productId = 200L)))

        val metrics = findMetrics(200L, EVENT_DATE)
        assertThat(metrics.likeCount).isEqualTo(1)
        assertThat(metrics.rankingScore).isCloseTo(0.2, within(1e-9))
        assertThat(scores[DailyProductKey(200L, EVENT_DATE)]).isCloseTo(0.2, within(1e-9))
    }

    @Test
    fun `좋아요 감소 이벤트는 like_count 1 감소, 스코어 0_2 감소`() {
        processor.process(listOf(likeIncreasedEvent(eventId = "evt-like-inc-2", productId = 300L)))
        val scores = processor.process(listOf(likeDecreasedEvent(eventId = "evt-like-dec-1", productId = 300L)))

        val metrics = findMetrics(300L, EVENT_DATE)
        assertThat(metrics.likeCount).isEqualTo(0)
        assertThat(metrics.rankingScore).isCloseTo(0.0, within(1e-9))
        assertThat(scores[DailyProductKey(300L, EVENT_DATE)]).isCloseTo(0.0, within(1e-9))
    }

    @Test
    fun `주문 이벤트는 아이템별로 order_count, sales_amount, 스코어를 반영한다`() {
        processor.process(
            listOf(
                paymentEvent(
                    eventId = "evt-pay-1",
                    items = listOf(
                        OrderItemInput(productId = 400L, quantity = 2, amount = 1_000L),
                        OrderItemInput(productId = 401L, quantity = 1, amount = 5_000L),
                    ),
                ),
            ),
        )

        val metrics400 = findMetrics(400L, EVENT_DATE)
        assertThat(metrics400.orderCount).isEqualTo(2)
        assertThat(metrics400.salesAmount).isEqualTo(1_000L)
        assertThat(metrics400.rankingScore).isCloseTo(0.6 * ln(1.0 + 1_000L), within(1e-9))

        val metrics401 = findMetrics(401L, EVENT_DATE)
        assertThat(metrics401.orderCount).isEqualTo(1)
        assertThat(metrics401.salesAmount).isEqualTo(5_000L)
        assertThat(metrics401.rankingScore).isCloseTo(0.6 * ln(1.0 + 5_000L), within(1e-9))
    }

    @Test
    fun `배치 내 같은 상품의 이벤트는 합산되어 한 번에 반영된다`() {
        val events = listOf(
            viewEvent(eventId = "evt-v1", productId = 500L),
            viewEvent(eventId = "evt-v2", productId = 500L),
            viewEvent(eventId = "evt-v3", productId = 500L),
            likeIncreasedEvent(eventId = "evt-l1", productId = 500L),
        )

        val scores = processor.process(events)

        val metrics = findMetrics(500L, EVENT_DATE)
        assertThat(metrics.viewCount).isEqualTo(3)
        assertThat(metrics.likeCount).isEqualTo(1)
        assertThat(metrics.rankingScore).isCloseTo(0.5, within(1e-9))
        assertThat(scores[DailyProductKey(500L, EVENT_DATE)]).isCloseTo(0.5, within(1e-9))
    }

    @Test
    fun `같은 eventId 를 가진 이벤트는 중복 처리되지 않는다`() {
        processor.process(listOf(viewEvent(eventId = "evt-dup-1", productId = 600L)))
        processor.process(listOf(viewEvent(eventId = "evt-dup-1", productId = 600L)))

        val metrics = findMetrics(600L, EVENT_DATE)
        assertThat(metrics.viewCount).isEqualTo(1)
    }

    @Test
    fun `같은 배치 내 동일 eventId 는 한 번만 처리된다`() {
        processor.process(
            listOf(
                viewEvent(eventId = "evt-batch-dup-1", productId = 620L),
                viewEvent(eventId = "evt-batch-dup-1", productId = 620L),
            ),
        )

        val metrics = findMetrics(620L, EVENT_DATE)
        assertThat(metrics.viewCount).isEqualTo(1)
    }

    @Test
    fun `중복 이벤트만 전달되어도 현재 SOT 점수가 반환된다`() {
        processor.process(listOf(viewEvent(eventId = "evt-dup-sot-1", productId = 610L)))

        val scores = processor.process(listOf(viewEvent(eventId = "evt-dup-sot-1", productId = 610L)))

        assertThat(scores[DailyProductKey(610L, EVENT_DATE)]).isCloseTo(0.1, within(1e-9))
    }

    @Test
    fun `이벤트 발생 시각은 한국 시간 기준 날짜로 변환한다`() {
        processor.process(
            listOf(viewEvent(eventId = "evt-zone-1", productId = 700L, occurredAt = "2026-07-13T15:00:00Z")),
        )

        val metrics = findMetrics(700L, LocalDate.of(2026, 7, 14))
        assertThat(metrics.viewCount).isEqualTo(1)
    }

    @Test
    fun `알 수 없는 이벤트 타입은 예외 없이 무시된다`() {
        val scores = processor.process(
            listOf(
                IncomingEvent(
                    eventId = "evt-unknown-1",
                    eventType = "SOMETHING_ELSE",
                    occurredAt = EVENT_OCCURRED_AT,
                    payload = mapOf("productId" to 800L),
                ),
            ),
        )

        assertThat(scores).isEmpty()
        assertThat(metricsRepository.findById(DailyProductRankingMetricsId(800L, EVENT_DATE))).isEmpty
    }

    @Test
    fun `처리 결과로 반환된 스코어는 절대값이며 캐시 갱신에 사용할 수 있다`() {
        processor.process(listOf(viewEvent(eventId = "evt-sd-0", productId = 900L)))

        val scores = processor.process(
            listOf(
                viewEvent(eventId = "evt-sd-1", productId = 900L),
                likeIncreasedEvent(eventId = "evt-sd-2", productId = 900L),
            ),
        )

        assertThat(scores).hasSize(1)
        assertThat(scores[DailyProductKey(900L, EVENT_DATE)]).isCloseTo(0.4, within(1e-9))
    }

    private fun findMetrics(productId: Long, date: LocalDate) =
        metricsRepository.findById(DailyProductRankingMetricsId(productId, date)).orElseThrow()

    private fun viewEvent(eventId: String, productId: Long, occurredAt: String = EVENT_OCCURRED_AT) =
        IncomingEvent(eventId, "PRODUCT_VIEWED", occurredAt, mapOf("productId" to productId))

    private fun likeIncreasedEvent(eventId: String, productId: Long) =
        IncomingEvent(eventId, "ProductLikeMetricIncreased", EVENT_OCCURRED_AT, mapOf("productId" to productId))

    private fun likeDecreasedEvent(eventId: String, productId: Long) =
        IncomingEvent(eventId, "ProductLikeMetricDecreased", EVENT_OCCURRED_AT, mapOf("productId" to productId))

    private fun paymentEvent(eventId: String, items: List<OrderItemInput>) =
        IncomingEvent(
            eventId,
            "PAYMENT_SUCCESS",
            EVENT_OCCURRED_AT,
            mapOf("items" to items.map { mapOf("productId" to it.productId, "quantity" to it.quantity, "amount" to it.amount) }),
        )

    private data class OrderItemInput(val productId: Long, val quantity: Int, val amount: Long)

    companion object {
        private val ZONE = ZoneId.of("Asia/Seoul")
        private val EVENT_DATE = LocalDate.of(2026, 7, 13)
        private val EVENT_OCCURRED_AT = EVENT_DATE.atStartOfDay(ZONE).toString()
    }
}
