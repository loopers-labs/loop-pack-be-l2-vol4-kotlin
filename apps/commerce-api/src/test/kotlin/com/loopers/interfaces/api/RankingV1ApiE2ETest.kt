package com.loopers.interfaces.api

import com.loopers.config.redis.RedisConfig
import com.loopers.infrastructure.brand.BrandJpaEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.interfaces.api.ranking.RankingV1Dto
import com.loopers.projection.product.ProductLikeCountProjectionEntity
import com.loopers.projection.product.ProductLikeCountQueryRepository
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val productLikeCountQueryRepository: ProductLikeCountQueryRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api/v1/rankings"
    }

    private lateinit var brand: BrandJpaEntity
    private val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    private val todayKey = "ranking:all:${today.format(DateTimeFormatter.BASIC_ISO_DATE)}"

    @BeforeEach
    fun setUp() {
        brand = brandJpaRepository.save(
            BrandJpaEntity(
                name = "TestBrand",
                description = "테스트 브랜드",
                logoImageUrl = null,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    inner class GetRankings {
        @Test
        fun `점수 높은 순으로 랭킹을 반환한다`() {
            // arrange
            val product1 = createProduct("상품A", 10_000L, 100, 5)
            val product2 = createProduct("상품B", 20_000L, 50, 10)
            val product3 = createProduct("상품C", 15_000L, 0, 3)
            seedScore(product1.id, 5.0)
            seedScore(product2.id, 30.0)
            seedScore(product3.id, 15.0)

            // act
            val response = getRankings()

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            val data = response.body?.data!!
            assertAll(
                { assertThat(data.items).hasSize(3) },
                { assertThat(data.items[0].rank).isEqualTo(1L) },
                { assertThat(data.items[0].productId).isEqualTo(product2.id) },
                { assertThat(data.items[0].name).isEqualTo("상품B") },
                { assertThat(data.items[0].brandName).isEqualTo("TestBrand") },
                { assertThat(data.items[0].price).isEqualTo(20_000L) },
                { assertThat(data.items[0].likeCount).isEqualTo(10) },
                { assertThat(data.items[0].soldOut).isFalse() },
                { assertThat(data.items[1].rank).isEqualTo(2L) },
                { assertThat(data.items[1].productId).isEqualTo(product3.id) },
                { assertThat(data.items[1].soldOut).isTrue() },
                { assertThat(data.items[2].rank).isEqualTo(3L) },
                { assertThat(data.items[2].productId).isEqualTo(product1.id) },
            )
        }

        @Test
        fun `페이지네이션이 동작한다`() {
            // arrange
            val products = (1..3).map { i ->
                createProduct("상품$i", 10_000L, 10, 0).also {
                    seedScore(it.id, (4 - i).toDouble())
                }
            }

            // act
            val response = getRankings(page = 1, size = 2)

            // assert
            val data = response.body?.data!!
            assertAll(
                { assertThat(data.items).hasSize(1) },
                { assertThat(data.items[0].rank).isEqualTo(3L) },
                { assertThat(data.items[0].productId).isEqualTo(products[2].id) },
                { assertThat(data.page).isEqualTo(1) },
                { assertThat(data.size).isEqualTo(2) },
                { assertThat(data.totalElements).isEqualTo(3L) },
                { assertThat(data.totalPages).isEqualTo(2) },
            )
        }

        @Test
        fun `범위를 벗어난 페이지를 요청해도 totalElements 와 totalPages 는 정확하다`() {
            // arrange
            (1..3).forEach { i ->
                createProduct("상품$i", 10_000L, 10, 0).also {
                    seedScore(it.id, i.toDouble())
                }
            }

            // act
            val response = getRankings(page = 2, size = 2)

            // assert
            val data = response.body?.data!!
            assertAll(
                { assertThat(data.items).isEmpty() },
                { assertThat(data.totalElements).isEqualTo(3L) },
                { assertThat(data.totalPages).isEqualTo(2) },
            )
        }

        @Test
        fun `응답에 score 필드가 포함되지 않는다`() {
            // arrange
            val product = createProduct("상품A", 10_000L, 10, 0)
            seedScore(product.id, 5.0)

            // act
            val responseType = object : ParameterizedTypeReference<Map<String, Any>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                null,
                responseType,
            )

            // assert
            val data = response.body?.get("data") as Map<*, *>
            val items = data["items"] as List<*>
            val firstItem = items[0] as Map<*, *>
            assertThat(firstItem.keys).doesNotContain("score")
        }

        @Test
        fun `랭킹 데이터가 없으면 빈 목록을 반환한다`() {
            // act
            val response = getRankings()

            // assert
            val data = response.body?.data!!
            assertAll(
                { assertThat(data.items).isEmpty() },
                { assertThat(data.totalElements).isEqualTo(0L) },
            )
        }

        @Test
        fun `잘못된 날짜 형식이면 BAD_REQUEST 를 반환한다`() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                "$ENDPOINT?date=invalid",
                HttpMethod.GET,
                null,
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    private fun createProduct(
        name: String,
        price: Long,
        stock: Int,
        likeCount: Int,
    ): ProductJpaEntity {
        val product = productJpaRepository.save(
            ProductJpaEntity(
                brandId = brand.id,
                name = name,
                description = "$name 설명",
                price = price,
            ),
        )
        stockJpaRepository.save(StockJpaEntity(productId = product.id, quantity = stock))
        productLikeCountQueryRepository.save(
            ProductLikeCountProjectionEntity(
                productId = product.id,
                brandId = brand.id,
                likeCount = likeCount,
            ),
        )
        return product
    }

    private fun seedScore(productId: Long, score: Double) {
        redisTemplate.opsForZSet().add(todayKey, productId.toString(), score)
    }

    private fun getRankings(
        date: String? = null,
        page: Int = 0,
        size: Int = 20,
    ): org.springframework.http.ResponseEntity<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>> {
        val responseType =
            object : ParameterizedTypeReference<ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>>() {}
        val url = buildString {
            append("$ENDPOINT?page=$page&size=$size")
            date?.let { append("&date=$it") }
        }
        return testRestTemplate.exchange(url, HttpMethod.GET, null, responseType)
    }
}
