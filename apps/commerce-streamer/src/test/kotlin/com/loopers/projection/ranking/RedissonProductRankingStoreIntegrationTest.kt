package com.loopers.projection.ranking

import com.loopers.projection.ranking.application.RankingKey
import com.loopers.projection.ranking.port.ProductRankingStore
import com.loopers.testcontainers.RedisTestContainerInitializer
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(initializers = [RedisTestContainerInitializer::class])
class RedissonProductRankingStoreIntegrationTest
    @Autowired
    constructor(
        private val productRankingStore: ProductRankingStore,
        private val redissonClient: RedissonClient,
    ) {
        @AfterEach
        fun tearDown() {
            redissonClient.keys.flushdb()
        }

        @Test
        fun `점수를_반영하면_상품_score_가_누적된다`() {
            val applied1 = productRankingStore.incrementScore(날짜, UUID.randomUUID(), 상품_ID, 1.0)
            val applied2 = productRankingStore.incrementScore(날짜, UUID.randomUUID(), 상품_ID, 4.0)

            assertThat(applied1).isTrue()
            assertThat(applied2).isTrue()
            assertThat(rankingScore(상품_ID)).isEqualTo(5.0)
        }

        @Test
        fun `동일_이벤트_상품을_재처리하면_점수가_반영되지_않는다`() {
            val eventId = UUID.randomUUID()
            productRankingStore.incrementScore(날짜, eventId, 상품_ID, 1.0)

            val reapplied = productRankingStore.incrementScore(날짜, eventId, 상품_ID, 1.0)

            assertThat(reapplied).isFalse()
            assertThat(rankingScore(상품_ID)).isEqualTo(1.0)
        }

        @Test
        fun `음수_delta_도_score_에_그대로_누적된다`() {
            productRankingStore.incrementScore(날짜, UUID.randomUUID(), 상품_ID, -1.0)

            assertThat(rankingScore(상품_ID)).isEqualTo(-1.0)
        }

        @Test
        fun `신규_랭킹_키에_TTL_이_설정된다`() {
            productRankingStore.incrementScore(날짜, UUID.randomUUID(), 상품_ID, 1.0)

            val remainMillis = rankingSet().remainTimeToLive()
            assertThat(remainMillis).isPositive()
            assertThat(remainMillis).isLessThanOrEqualTo(RankingKey.TTL_SECONDS * 1000)
        }

        @Test
        fun `이미_TTL_이_있는_랭킹_키의_TTL_은_덮어쓰지_않는다`() {
            productRankingStore.incrementScore(날짜, UUID.randomUUID(), 상품_ID, 1.0)
            rankingSet().expire(Duration.ofSeconds(60))

            productRankingStore.incrementScore(날짜, UUID.randomUUID(), 상품_ID, 1.0)

            assertThat(rankingSet().remainTimeToLive()).isLessThanOrEqualTo(60_000L)
        }

        private fun rankingSet() = redissonClient.getScoredSortedSet<String>(RankingKey.daily(날짜))

        private fun rankingScore(productId: Long): Double? = rankingSet().getScore(productId.toString())

        companion object {
            private val 날짜 = LocalDate.of(2026, 7, 17)
            private const val 상품_ID = 1L
        }
    }
