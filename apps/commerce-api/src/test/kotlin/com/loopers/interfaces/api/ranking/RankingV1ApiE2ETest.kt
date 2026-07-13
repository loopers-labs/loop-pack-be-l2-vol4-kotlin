package com.loopers.interfaces.api.ranking

import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RedisConfig
import com.loopers.infrastructure.brand.entity.BrandEntity
import com.loopers.infrastructure.brand.repository.BrandJpaRepository
import com.loopers.infrastructure.product.entity.ProductEntity
import com.loopers.infrastructure.product.entity.ProductStatEntity
import com.loopers.infrastructure.product.repository.ProductJpaRepository
import com.loopers.infrastructure.product.repository.ProductStatJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.utils.DatabaseCleanUp
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
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStatJpaRepository: ProductStatJpaRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("명시한 날짜의 랭킹을 Redis 순서와 1-based rank로 페이지 조회한다")
    @Test
    fun returnsRankingPageForRequestedDate() {
        val date = LocalDate.of(2026, 7, 13)
        val brand = createBrand("loopers")
        val first = createProduct(brand.id, "rank-first", likeCount = 7L)
        val second = createProduct(brand.id, "rank-second", likeCount = 5L)
        val third = createProduct(brand.id, "rank-third", likeCount = 3L)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(date), third.id.toString(), 10.0)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(date), first.id.toString(), 30.0)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(date), second.id.toString(), 20.0)

        val response = getRankings(date = date, page = 0, size = 2)

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.data).hasSize(2) },
            { assertThat(response.body?.data?.data?.map { it.productId }).containsExactly(first.id, second.id) },
            { assertThat(response.body?.data?.data?.map { it.rank }).containsExactly(1L, 2L) },
            { assertThat(response.body?.data?.data?.map { it.score }).containsExactly(30.0, 20.0) },
            { assertThat(response.body?.data?.data?.first()?.brandName).isEqualTo(brand.name) },
            { assertThat(response.body?.data?.data?.first()?.likeCount).isEqualTo(7L) },
            { assertThat(response.body?.data?.meta?.totalElements).isEqualTo(3L) },
            { assertThat(response.body?.data?.meta?.totalPages).isEqualTo(2) },
        )
    }

    @DisplayName("랭킹 key가 없으면 빈 페이지를 반환한다")
    @Test
    fun returnsEmptyPageWhenRankingDoesNotExist() {
        val response = getRankings(date = LocalDate.of(2026, 7, 12), page = 0, size = 20)

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.data).isEmpty() },
            { assertThat(response.body?.data?.meta?.totalElements).isZero() },
        )
    }

    private fun getRankings(
        date: LocalDate,
        page: Int,
        size: Int,
    ) = testRestTemplate.exchange(
        "/api/v1/rankings?date=${date.format(DateTimeFormatter.BASIC_ISO_DATE)}&page=$page&size=$size",
        HttpMethod.GET,
        null,
        object : ParameterizedTypeReference<ApiResponse<PageResponse<RankingResponse>>>() {},
    )

    private fun createBrand(name: String): BrandEntity {
        return brandJpaRepository.save(
            BrandEntity(
                name = name,
                description = "$name brand",
                logoImageUrl = "https://image.loopers/$name.png",
            ),
        )
    }

    private fun createProduct(
        brandId: Long,
        name: String,
        likeCount: Long,
    ): ProductEntity {
        val product = productJpaRepository.save(
            ProductEntity(
                brandId = brandId,
                name = name,
                price = 10_000L,
                description = "$name product",
                imageUrl = "https://image.loopers/$name.png",
            ),
        )
        productStatJpaRepository.save(
            ProductStatEntity(
                productId = product.id,
                brandId = brandId,
                likeCount = likeCount,
            ),
        )
        return product
    }

    data class RankingResponse(
        val productId: Long,
        val productName: String,
        val price: Long,
        val imageUrl: String,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
        val rank: Long,
        val score: Double,
    )
}
