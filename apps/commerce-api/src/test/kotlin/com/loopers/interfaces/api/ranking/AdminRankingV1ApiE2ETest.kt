package com.loopers.interfaces.api.ranking

import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RedisConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminRankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("가중치를 변경하면 활성 정책 저장과 오늘 랭킹 재계산이 원자적으로 반영된다")
    @Test
    fun updatesWeightsAndRebuildsTodayRanking() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        redisTemplate.opsForZSet().add(RankingRedisKeys.view(today), "1", 10.0)
        redisTemplate.opsForZSet().add(RankingRedisKeys.like(today), "2", 20.0)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(today), "1", 0.5)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(today), "2", 8.0)

        val response = updateWeights(viewWeight = 1.0, likeWeight = 0.0, salesWeight = 0.0)

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            {
                assertThat(redisTemplate.opsForHash<String, String>().entries(RankingRedisKeys.ACTIVE_WEIGHTS))
                    .containsEntry("view", "1.0")
                    .containsEntry("like", "0.0")
                    .containsEntry("sales", "0.0")
            },
            { assertThat(redisTemplate.opsForZSet().reverseRange(RankingRedisKeys.all(today), 0, -1)).containsExactly("1", "2") },
            { assertThat(redisTemplate.opsForZSet().score(RankingRedisKeys.all(today), "1") ?: Double.NaN).isEqualTo(10.0) },
            { assertThat(redisTemplate.getExpire(RankingRedisKeys.all(today))).isPositive() },
        )
    }

    @DisplayName("음수 가중치는 400으로 거절한다")
    @Test
    fun rejectsNegativeWeight() {
        val response = updateWeights(viewWeight = -0.1, likeWeight = 0.4, salesWeight = 1.0)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    private fun updateWeights(
        viewWeight: Double,
        likeWeight: Double,
        salesWeight: Double,
    ) = testRestTemplate.exchange(
        "/api-admin/v1/rankings/weights",
        HttpMethod.PUT,
        HttpEntity(
            mapOf(
                "viewWeight" to viewWeight,
                "likeWeight" to likeWeight,
                "salesWeight" to salesWeight,
            ),
            HttpHeaders().apply {
                set("X-Loopers-Ldap", "loopers.admin")
                contentType = MediaType.APPLICATION_JSON
            },
        ),
        String::class.java,
    )
}
