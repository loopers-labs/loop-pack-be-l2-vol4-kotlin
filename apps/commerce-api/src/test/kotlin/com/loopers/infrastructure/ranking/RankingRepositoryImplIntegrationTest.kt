package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.time.LocalDate

@SpringBootTest
class RankingRepositoryImplIntegrationTest @Autowired constructor(
    private val rankingRepository: RankingRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    private val today = LocalDate.of(2026, 7, 13)

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("findTopN")
    @Nested
    inner class FindTopN {
        @Test
        fun `점수 높은 순으로 상품을 반환한다`() {
            // arrange
            seedScore(1L, 10.0)
            seedScore(2L, 30.0)
            seedScore(3L, 20.0)

            // act
            val result = rankingRepository.findTopN(today, offset = 0, count = 3)

            // assert
            assertThat(result).hasSize(3)
            assertThat(result[0].productId).isEqualTo(2L)
            assertThat(result[0].rank).isEqualTo(1L)
            assertThat(result[1].productId).isEqualTo(3L)
            assertThat(result[1].rank).isEqualTo(2L)
            assertThat(result[2].productId).isEqualTo(1L)
            assertThat(result[2].rank).isEqualTo(3L)
        }

        @Test
        fun `offset 을 적용하면 해당 위치부터 반환하고 rank 도 그에 맞게 계산된다`() {
            // arrange
            seedScore(1L, 10.0)
            seedScore(2L, 30.0)
            seedScore(3L, 20.0)

            // act
            val result = rankingRepository.findTopN(today, offset = 1, count = 2)

            // assert
            assertThat(result).hasSize(2)
            assertThat(result[0].productId).isEqualTo(3L)
            assertThat(result[0].rank).isEqualTo(2L)
            assertThat(result[1].productId).isEqualTo(1L)
            assertThat(result[1].rank).isEqualTo(3L)
        }

        @Test
        fun `데이터가 없으면 빈 리스트를 반환한다`() {
            // act
            val result = rankingRepository.findTopN(today, offset = 0, count = 10)

            // assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `count 가 0이면 데이터가 있어도 빈 리스트를 반환한다`() {
            // arrange
            seedScore(1L, 10.0)
            seedScore(2L, 20.0)

            // act
            val result = rankingRepository.findTopN(today, offset = 0, count = 0)

            // assert
            assertThat(result).isEmpty()
        }

        @Test
        fun `offset 이 음수이면 예외가 발생한다`() {
            // act & assert
            assertThatThrownBy {
                rankingRepository.findTopN(today, offset = -1, count = 10)
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `count 가 음수이면 예외가 발생한다`() {
            // act & assert
            assertThatThrownBy {
                rankingRepository.findTopN(today, offset = 0, count = -1)
            }.isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `조회 날짜에 해당하는 랭킹만 반환한다`() {
            // arrange
            val yesterday = today.minusDays(1)
            seedScore(today, productId = 1L, score = 10.0)
            seedScore(yesterday, productId = 2L, score = 20.0)

            // act
            val todayResult = rankingRepository.findTopN(today, offset = 0, count = 10)
            val yesterdayResult = rankingRepository.findTopN(yesterday, offset = 0, count = 10)

            // assert
            assertThat(todayResult.map { it.productId }).containsExactly(1L)
            assertThat(yesterdayResult.map { it.productId }).containsExactly(2L)
        }

        @Test
        fun `동점 상품은 Redis member 역 사전순으로 서로 다른 순위를 가진다`() {
            // arrange
            seedScore(productId = 9L, score = 10.0)
            seedScore(productId = 10L, score = 10.0)

            // act
            val result = rankingRepository.findTopN(today, offset = 0, count = 2)

            // assert
            assertThat(result.map { it.productId }).containsExactly(9L, 10L)
            assertThat(result.map { it.rank }).containsExactly(1L, 2L)
        }
    }

    @DisplayName("countByDate")
    @Nested
    inner class CountByDate {
        @Test
        fun `해당 날짜에 등록된 상품 수를 반환한다`() {
            // arrange
            seedScore(1L, 10.0)
            seedScore(2L, 20.0)

            // act & assert
            assertThat(rankingRepository.countByDate(today)).isEqualTo(2L)
        }

        @Test
        fun `데이터가 없으면 0을 반환한다`() {
            // act & assert
            assertThat(rankingRepository.countByDate(today)).isEqualTo(0L)
        }
    }

    @DisplayName("findRank")
    @Nested
    inner class FindRank {
        @Test
        fun `점수가 가장 높은 상품의 rank 는 1이다`() {
            // arrange
            seedScore(1L, 10.0)
            seedScore(2L, 30.0)
            seedScore(3L, 20.0)

            // act & assert
            assertThat(rankingRepository.findRank(today, 2L)).isEqualTo(1L)
        }

        @Test
        fun `랭킹에 없는 상품은 null 을 반환한다`() {
            // arrange
            seedScore(1L, 10.0)

            // act & assert
            assertThat(rankingRepository.findRank(today, 999L)).isNull()
        }
    }

    private fun seedScore(productId: Long, score: Double) {
        seedScore(today, productId, score)
    }

    private fun seedScore(date: LocalDate, productId: Long, score: Double) {
        val key = RankingKeyGenerator.daily(date)
        redisTemplate.opsForZSet().add(key, productId.toString(), score)
    }
}
