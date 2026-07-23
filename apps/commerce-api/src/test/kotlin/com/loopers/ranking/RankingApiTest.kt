package com.loopers.ranking

import com.loopers.domain.ranking.RankingQueryService
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 랭킹 시스템 통합 테스트.
 * - ZSET 점수 반영 → API 조회 E2E 흐름
 * - 일자 변경 시 이전 날짜 랭킹 조회
 * - 가중치 적용이 랭킹 순서에 반영되는지 확인
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingApiTest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val jdbcTemplate: JdbcTemplate,
    @Qualifier("masterRedisTemplate")
    private val redisTemplate: RedisTemplate<String, String>,
    private val rankingQueryService: RankingQueryService,
) {

    private val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    private val rankingKey = "ranking:all:$today"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "INSERT INTO brands (id, name, created_at, updated_at) VALUES (1, '테스트브랜드', NOW(), NOW())",
        )
        jdbcTemplate.execute(
            "INSERT INTO products (id, name, price, stock, brand_id, like_count, created_at, updated_at)" +
                " VALUES (1, '상품A', 10000, 100, 1, 0, NOW(), NOW())",
        )
        jdbcTemplate.execute(
            "INSERT INTO products (id, name, price, stock, brand_id, like_count, created_at, updated_at)" +
                " VALUES (2, '상품B', 20000, 100, 1, 0, NOW(), NOW())",
        )
        jdbcTemplate.execute(
            "INSERT INTO products (id, name, price, stock, brand_id, like_count, created_at, updated_at)" +
                " VALUES (3, '상품C', 5000, 100, 1, 0, NOW(), NOW())",
        )
    }

    @DisplayName("ZSET에 점수가 반영되면 Ranking API에서 Top-N으로 조회된다")
    @Test
    fun rankingApi_returnsTopN() {
        // 점수 직접 설정 (상품B > 상품A > 상품C)
        redisTemplate.opsForZSet().add(rankingKey, "2", 50.0)
        redisTemplate.opsForZSet().add(rankingKey, "1", 30.0)
        redisTemplate.opsForZSet().add(rankingKey, "3", 10.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?date=$today&size=10&page=0",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any>>>>() {},
        )

        response.statusCode.is2xxSuccessful shouldBe true
        val data = response.body?.data!!
        data shouldHaveSize 3
        (data[0]["productId"] as Number).toLong() shouldBe 2L
        (data[0]["productName"] as String) shouldBe "상품B"
        (data[1]["productId"] as Number).toLong() shouldBe 1L
        (data[2]["productId"] as Number).toLong() shouldBe 3L
    }

    @DisplayName("이전 날짜의 랭킹도 정상 조회된다")
    @Test
    fun rankingApi_previousDate() {
        val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val yesterdayKey = "ranking:all:$yesterday"

        redisTemplate.opsForZSet().add(yesterdayKey, "1", 100.0)
        redisTemplate.opsForZSet().add(yesterdayKey, "3", 80.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?date=$yesterday&size=10&page=0",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any>>>>() {},
        )

        response.statusCode.is2xxSuccessful shouldBe true
        val data = response.body?.data!!
        data shouldHaveSize 2
        (data[0]["productId"] as Number).toLong() shouldBe 1L
        (data[1]["productId"] as Number).toLong() shouldBe 3L
    }

    @DisplayName("가중치 적용: 주문 1건(0.7×log) > 좋아요 3건(0.2×3)")
    @Test
    fun weightedScore_orderOutweighsLikes() {
        // 상품A: 좋아요 3건 → 0.2 × 3 = 0.6
        redisTemplate.opsForZSet().incrementScore(rankingKey, "1", 0.2)
        redisTemplate.opsForZSet().incrementScore(rankingKey, "1", 0.2)
        redisTemplate.opsForZSet().incrementScore(rankingKey, "1", 0.2)

        // 상품B: 주문 1건 (가격 10000 × 수량 1) → 0.7 × log10(10001) ≈ 0.7 × 4.0 = 2.8
        val orderScore = 0.7 * Math.log10(10000.0 * 1 + 1)
        redisTemplate.opsForZSet().incrementScore(rankingKey, "2", orderScore)

        val rankings = rankingQueryService.getRankingPage(null, today, 0, 10)
        rankings[0].productId shouldBe 2L
        rankings[1].productId shouldBe 1L
    }

    @DisplayName("상품 상세 조회 시 랭킹 순위가 포함된다")
    @Test
    fun productDetail_includesRanking() {
        redisTemplate.opsForZSet().add(rankingKey, "1", 50.0)
        redisTemplate.opsForZSet().add(rankingKey, "2", 30.0)

        val response = testRestTemplate.exchange(
            "/api/v1/products/1",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any>>>() {},
        )

        response.statusCode.is2xxSuccessful shouldBe true
        val data = response.body?.data!!
        val ranking = data["ranking"] as Map<*, *>
        (ranking["rank"] as Number).toLong() shouldBe 1L
    }

    @DisplayName("랭킹에 없는 상품은 ranking이 null로 반환된다")
    @Test
    fun productDetail_noRanking_returnsNull() {
        val response = testRestTemplate.exchange(
            "/api/v1/products/3",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {},
        )

        response.statusCode.is2xxSuccessful shouldBe true
        val data = response.body?.data!!
        data["ranking"].shouldBeNull()
    }
}
