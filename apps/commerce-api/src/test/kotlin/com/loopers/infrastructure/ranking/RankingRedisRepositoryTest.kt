package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingQueryRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate

@SpringBootTest
class RankingRedisRepositoryTest @Autowired constructor(
    private val repository: RankingQueryRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    private val key = "ranking:all:v1:20260717"

    @BeforeEach
    fun seed() {
        redisTemplate.opsForZSet().add(key, "1", 5.0)
        redisTemplate.opsForZSet().add(key, "2", 3.0)
        redisTemplate.opsForZSet().add(key, "3", 1.0)
    }

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("page는 score 내림차순 구간을, total은 전체 수를 반환한다.")
    @Test
    fun pageAndTotal() {
        val page = repository.page(key, offset = 0, size = 2)
        assertThat(page.map { it.productId }).containsExactly(1L, 2L)
        assertThat(page[0].score).isEqualTo(5.0)
        assertThat(repository.total(key)).isEqualTo(3L)
        assertThat(repository.page(key, offset = 2, size = 2).map { it.productId }).containsExactly(3L)
    }

    @DisplayName("rank는 0-based 순위를, 미진입 상품·빈 키는 null/빈 목록을 반환한다.")
    @Test
    fun rankAndMisses() {
        assertThat(repository.rank(key, 1L)).isEqualTo(0L)
        assertThat(repository.rank(key, 3L)).isEqualTo(2L)
        assertThat(repository.rank(key, 999L)).isNull()
        assertThat(repository.page("ranking:all:v1:19990101", 0, 10)).isEmpty()
        assertThat(repository.total("ranking:all:v1:19990101")).isZero()
    }
}
