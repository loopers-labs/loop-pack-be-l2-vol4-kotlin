package com.loopers.projection.ranking

import com.loopers.projection.ranking.application.RankingCarryOverService
import com.loopers.projection.ranking.application.RankingKey
import com.loopers.testcontainers.RedisTestContainerInitializer
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
class RankingCarryOverServiceIntegrationTest
    @Autowired
    constructor(
        private val rankingCarryOverService: RankingCarryOverService,
        private val redissonClient: RedissonClient,
    ) {
        @AfterEach
        fun tearDown() {
            redissonClient.keys.flushdb()
        }

        @Test
        fun `당일_점수에_감쇠_계수를_곱해_다음날_랭킹판을_사전_생성한다`() {
            todaySet().add(10.0, "1")
            todaySet().add(4.0, "2")

            rankingCarryOverService.carryOver()

            assertThat(tomorrowScore("1")).isEqualTo(5.0)
            assertThat(tomorrowScore("2")).isEqualTo(2.0)
            assertThat(tomorrowSet().remainTimeToLive()).isPositive()
        }

        @Test
        fun `감쇠_후_min_score_미만_상품은_다음날_랭킹판에서_제거한다`() {
            todaySet().add(10.0, "1")
            todaySet().add(1.5, "2")

            rankingCarryOverService.carryOver()

            assertThat(tomorrowScore("1")).isEqualTo(5.0)
            assertThat(tomorrowScore("2")).isNull()
        }

        @Test
        fun `이미_수행한_날짜에_재호출하면_다음날_랭킹판을_변경하지_않는다`() {
            todaySet().add(10.0, "1")
            rankingCarryOverService.carryOver()
            todaySet().add(90.0, "1")

            rankingCarryOverService.carryOver()

            assertThat(tomorrowScore("1")).isEqualTo(5.0)
        }

        @Test
        fun `당일_랭킹판이_없으면_다음날_랭킹판을_만들지_않는다`() {
            rankingCarryOverService.carryOver()

            assertThat(tomorrowSet().isExists).isFalse()
        }

        private fun todaySet(): RScoredSortedSet<String> =
            redissonClient.getScoredSortedSet(RankingKey.daily(오늘))

        private fun tomorrowSet(): RScoredSortedSet<String> =
            redissonClient.getScoredSortedSet(RankingKey.daily(오늘.plusDays(1)))

        private fun tomorrowScore(member: String): Double? = tomorrowSet().getScore(member)

        companion object {
            private val 오늘: LocalDate = LocalDate.now(RankingKey.ZONE)
        }
    }
