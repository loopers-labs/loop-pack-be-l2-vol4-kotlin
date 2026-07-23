package com.loopers.interfaces.api.ranking

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingUnavailableException
import com.loopers.infrastructure.brand.entity.BrandEntity
import com.loopers.infrastructure.brand.repository.BrandJpaRepository
import com.loopers.infrastructure.product.entity.ProductEntity
import com.loopers.infrastructure.product.entity.ProductStatEntity
import com.loopers.infrastructure.product.repository.ProductJpaRepository
import com.loopers.infrastructure.product.repository.ProductStatJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingAvailabilityV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val objectMapper: ObjectMapper,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStatJpaRepository: ProductStatJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoBean
    private lateinit var rankingRepository: RankingRepository

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("Redis 랭킹 저장소 장애 시 랭킹 API는 503을 반환한다")
    @Test
    fun returnsServiceUnavailableForRankingApi() {
        whenever(rankingRepository.findPage(any(), any(), any(), any()))
            .thenThrow(RankingUnavailableException(IllegalStateException("redis unavailable")))
        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

        val response = testRestTemplate.getForEntity(
            "/api/v1/rankings?date=$date&page=0&size=20",
            String::class.java,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
    }

    @DisplayName("Redis 랭킹 저장소 장애여도 상품 상세는 200과 rank null을 반환한다")
    @Test
    fun degradesOnlyRankForProductDetail() {
        val product = createProduct()
        whenever(rankingRepository.findRank(any(), any()))
            .thenThrow(RankingUnavailableException(IllegalStateException("redis unavailable")))

        val response = testRestTemplate.getForEntity("/api/v1/products/${product.id}", String::class.java)
        val body = objectMapper.readTree(response.body)

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(body.path("data").path("productId").asLong()).isEqualTo(product.id) },
            { assertThat(body.path("data").path("rank").isNull).isTrue() },
        )
    }

    private fun createProduct(): ProductEntity {
        val brand = brandJpaRepository.save(
            BrandEntity(
                name = "loopers",
                description = "loopers brand",
                logoImageUrl = "https://image.loopers/loopers.png",
            ),
        )
        val product = productJpaRepository.save(
            ProductEntity(
                brandId = brand.id,
                name = "loopers hoodie",
                price = 10_000L,
                description = "loopers product",
                imageUrl = "https://image.loopers/hoodie.png",
            ),
        )
        productStatJpaRepository.save(
            ProductStatEntity(
                productId = product.id,
                brandId = brand.id,
                likeCount = 0L,
            ),
        )
        return product
    }
}
