package com.loopers.interfaces.api.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RedisConfig
import com.loopers.infrastructure.brand.entity.BrandEntity
import com.loopers.infrastructure.brand.repository.BrandJpaRepository
import com.loopers.infrastructure.product.entity.ProductEntity
import com.loopers.infrastructure.product.entity.ProductStatEntity
import com.loopers.infrastructure.product.repository.ProductJpaRepository
import com.loopers.infrastructure.product.repository.ProductStatJpaRepository
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
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductRankV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val objectMapper: ObjectMapper,
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

    @DisplayName("상품 상세는 캐시와 무관하게 오늘의 최신 1-based rank를 반환한다")
    @Test
    fun returnsLatestRankWithProductDetail() {
        val (first, second) = createTwoProducts()
        val key = RankingRedisKeys.all(today())
        redisTemplate.opsForZSet().add(key, first.id.toString(), 10.0)
        redisTemplate.opsForZSet().add(key, second.id.toString(), 20.0)

        val firstResponse = testRestTemplate.getForEntity("/api/v1/products/${first.id}", String::class.java)
        redisTemplate.opsForZSet().add(key, first.id.toString(), 30.0)
        val secondResponse = testRestTemplate.getForEntity("/api/v1/products/${first.id}", String::class.java)

        assertAll(
            { assertThat(firstResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(objectMapper.readTree(firstResponse.body).path("data").path("rank").asLong()).isEqualTo(2L) },
            { assertThat(secondResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(objectMapper.readTree(secondResponse.body).path("data").path("rank").asLong()).isEqualTo(1L) },
        )
    }

    @DisplayName("오늘 랭킹에 없는 상품 상세는 rank null을 반환한다")
    @Test
    fun returnsNullRankForUnrankedProduct() {
        val (product) = createTwoProducts()

        val response = testRestTemplate.getForEntity("/api/v1/products/${product.id}", String::class.java)
        val rankNode = objectMapper.readTree(response.body).path("data").path("rank")

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(rankNode.isNull).isTrue() },
        )
    }

    private fun createTwoProducts(): Pair<ProductEntity, ProductEntity> {
        val brand = brandJpaRepository.save(
            BrandEntity(
                name = "loopers",
                description = "loopers brand",
                logoImageUrl = "https://image.loopers/loopers.png",
            ),
        )
        return createProduct(brand.id, "first") to createProduct(brand.id, "second")
    }

    private fun createProduct(brandId: Long, name: String): ProductEntity {
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
                likeCount = 0L,
            ),
        )
        return product
    }

    private fun today(): LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
}
