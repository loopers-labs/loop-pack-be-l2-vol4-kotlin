package com.loopers.projection.ranking

import com.loopers.projection.ranking.application.RankingKey
import com.loopers.projection.ranking.application.RankingRdbSyncService
import com.loopers.projection.ranking.infrastructure.persistence.ProductRankingDailyJpaRepository
import com.loopers.testcontainers.RedisTestContainerInitializer
import com.loopers.utils.DatabaseCleanUp
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.redisson.api.RScoredSortedSet
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(initializers = [RedisTestContainerInitializer::class])
class RankingRdbSyncServiceIntegrationTest
    @Autowired
    constructor(
        private val rankingRdbSyncService: RankingRdbSyncService,
        private val productRankingDailyJpaRepository: ProductRankingDailyJpaRepository,
        private val redissonClient: RedissonClient,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
            redissonClient.keys.flushdb()
        }

        @Test
        fun `당일_랭킹판_상위_항목을_rank_순으로_스냅샷_테이블에_저장한다`() {
            todaySet().add(5.0, "1")
            todaySet().add(9.0, "2")
            todaySet().add(1.0, "3")

            rankingRdbSyncService.sync()

            val rows = productRankingDailyJpaRepository.findByIdRankingDateOrderByRankNoAsc(오늘)
            assertThat(rows.map { it.id.productId }).containsExactly(2L, 1L, 3L)
            assertThat(rows.map { it.rankNo }).containsExactly(1, 2, 3)
            assertThat(rows.map { it.score }).containsExactly(9.0, 5.0, 1.0)
        }

        @Test
        fun `재실행하면_최신_랭킹판으로_스냅샷을_교체한다`() {
            todaySet().add(5.0, "1")
            todaySet().add(9.0, "2")
            rankingRdbSyncService.sync()
            todaySet().add(10.0, "3")
            todaySet().remove("2")

            rankingRdbSyncService.sync()

            val rows = productRankingDailyJpaRepository.findByIdRankingDateOrderByRankNoAsc(오늘)
            assertThat(rows.map { it.id.productId }).containsExactly(3L, 1L)
            assertThat(rows.map { it.rankNo }).containsExactly(1, 2)
        }

        @Test
        fun `당일_랭킹판이_비어_있으면_스냅샷을_변경하지_않는다`() {
            rankingRdbSyncService.sync()

            assertThat(productRankingDailyJpaRepository.count()).isZero()
        }

        private fun todaySet(): RScoredSortedSet<String> =
            redissonClient.getScoredSortedSet(RankingKey.daily(오늘))

        companion object {
            private val 오늘: LocalDate = LocalDate.now(RankingKey.ZONE)
        }
    }
