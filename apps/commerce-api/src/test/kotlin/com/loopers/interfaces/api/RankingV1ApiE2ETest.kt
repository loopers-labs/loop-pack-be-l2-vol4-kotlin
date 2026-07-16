package com.loopers.interfaces.api

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.RankingKey
import com.loopers.interfaces.api.ranking.RankingV1Dto
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainersConfig::class)
class RankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun todayKey() = RankingKey.of(LocalDate.now(ZoneId.of("Asia/Seoul")))

    private fun seedScore(productId: Long, score: Double) {
        masterTemplate.opsForZSet().add(todayKey(), productId.toString(), score)
    }

    @Test
    @DisplayName("GET /api/v1/rankings 는 점수 내림차순으로 상품 정보가 조립된 목록을 200 으로 반환한다")
    fun returnsRankingDesc() {
        val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
        val lower = productRepository.save(ProductFixture.validProduct(name = "A", price = 1000, brandId = brandId)).id
        val higher = productRepository.save(ProductFixture.validProduct(name = "B", price = 2000, brandId = brandId)).id
        seedScore(lower, 1.0)
        seedScore(higher, 3.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val content = response.body!!.data!!.content
        assertThat(content.map { it.productId }).containsExactly(higher, lower)
        assertThat(content.first().rank).isEqualTo(1L)
        assertThat(content.first().brandName).isEqualTo("나이키")
    }
}
