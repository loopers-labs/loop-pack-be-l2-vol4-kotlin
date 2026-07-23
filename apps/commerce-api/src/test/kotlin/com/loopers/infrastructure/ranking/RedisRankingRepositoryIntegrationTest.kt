package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SpringBootTest
class RedisRankingRepositoryIntegrationTest @Autowired constructor(
    private val rankingRepository: RankingRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() = redisCleanUp.truncateAll()

    private val date = LocalDate.of(2026, 7, 15)

    private fun key(): String = "ranking:" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    private fun seed(productId: Long, score: Double) {
        masterTemplate.opsForZSet().add(key(), productId.toString(), score)
    }

    private fun seedAt(rawKey: String, productId: Long, score: Double) {
        masterTemplate.opsForZSet().add(rawKey, productId.toString(), score)
    }

    @DisplayName("점수 내림차순으로 1-based 순위를 매겨 페이지를 반환한다")
    @Test
    fun returnsEntriesInScoreDescOrderWithRank() {
        seed(100L, 30.0)
        seed(200L, 20.0)
        seed(300L, 10.0)

        val page = rankingRepository.findPage(RankingPeriod.DAILY, date, PageRequest.of(0, 10))

        assertThat(page.total).isEqualTo(3L)
        assertThat(page.entries).containsExactly(
            RankingEntry(100L, 1L),
            RankingEntry(200L, 2L),
            RankingEntry(300L, 3L),
        )
    }

    @DisplayName("페이지 오프셋이 순위에 반영된다")
    @Test
    fun paginationOffsetReflectedInRank() {
        seed(10L, 50.0)
        seed(20L, 40.0)
        seed(30L, 30.0)
        seed(40L, 20.0)
        seed(50L, 10.0)

        val page = rankingRepository.findPage(RankingPeriod.DAILY, date, PageRequest.of(1, 2))

        assertThat(page.total).isEqualTo(5L)
        assertThat(page.entries).containsExactly(
            RankingEntry(30L, 3L),
            RankingEntry(40L, 4L),
        )
    }

    @DisplayName("특정 상품의 순위를 1-based 로 반환하고, 랭킹에 없으면 null 이다")
    @Test
    fun findRankReturnsOneBasedOrNull() {
        seed(100L, 30.0)
        seed(200L, 20.0)
        seed(300L, 10.0)

        assertThat(rankingRepository.findRank(RankingPeriod.DAILY, date, 200L)).isEqualTo(2L)
        assertThat(rankingRepository.findRank(RankingPeriod.DAILY, date, 999L)).isNull()
    }

    @DisplayName("데이터가 없는 날짜는 빈 페이지를 반환한다")
    @Test
    fun emptyDateReturnsEmptyPage() {
        seed(100L, 30.0)

        val page = rankingRepository.findPage(RankingPeriod.DAILY, LocalDate.of(2026, 1, 1), PageRequest.of(0, 10))

        assertThat(page.entries).isEmpty()
        assertThat(page.total).isEqualTo(0L)
    }

    @DisplayName("WEEKLY 는 배치가 쓰는 ranking:weekly:{ISO주} 키를 date 로부터 파생해 조회한다")
    @Test
    fun weeklyDerivesIsoWeekKey() {
        // 배치(AggregationPeriod.weeklyOf)가 2026-07-20~26 주에 대해 쓰는 키
        seedAt("ranking:weekly:2026-W30", 100L, 5.6)
        seedAt("ranking:weekly:2026-W30", 200L, 1.0)

        val page = rankingRepository.findPage(RankingPeriod.WEEKLY, LocalDate.of(2026, 7, 22), PageRequest.of(0, 10))

        assertThat(page.total).isEqualTo(2L)
        assertThat(page.entries).containsExactly(
            RankingEntry(100L, 1L),
            RankingEntry(200L, 2L),
        )
    }

    @DisplayName("MONTHLY 는 배치가 쓰는 ranking:monthly:{yyyy-MM} 키를 date 로부터 파생해 조회한다")
    @Test
    fun monthlyDerivesYearMonthKey() {
        seedAt("ranking:monthly:2026-07", 100L, 5.6)
        seedAt("ranking:monthly:2026-07", 200L, 1.0)

        val page = rankingRepository.findPage(RankingPeriod.MONTHLY, LocalDate.of(2026, 7, 22), PageRequest.of(0, 10))

        assertThat(page.total).isEqualTo(2L)
        assertThat(page.entries).containsExactly(
            RankingEntry(100L, 1L),
            RankingEntry(200L, 2L),
        )
    }
}
