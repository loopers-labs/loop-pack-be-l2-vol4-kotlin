package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    inner class GetProducts {
        @DisplayName("기본 page/size와 좋아요순 정렬 메타를 반환한다.")
        @Test
        fun returnsProductPage_withDefaultPaging() {
            // arrange
            val brand = brandRepository.save(BrandModel(name = "Nike", description = "Shoes"))
            val top = saveProduct(brandId = brand.id, name = "Top", likeCount = 30, stockQuantity = 9)
            val second = saveProduct(brandId = brand.id, name = "Second", likeCount = 20, stockQuantity = 5)
            val third = saveProduct(brandId = brand.id, name = "Third", likeCount = 10, stockQuantity = 1)

            // act
            val response = getProducts("/api/v1/products?sort=likes_desc")

            // assert
            val body = checkNotNull(response.body) { "response.body must not be null" }
            val data = checkNotNull(body.data) { "response.body.data must not be null" }
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data.items.map { it.id }).containsExactly(top.id, second.id, third.id) },
                { assertThat(data.items.first().stockQuantity).isEqualTo(9) },
                { assertThat(data.page).isEqualTo(0) },
                { assertThat(data.size).isEqualTo(20) },
                { assertThat(data.totalCount).isEqualTo(3) },
                { assertThat(data.totalPages).isEqualTo(1) },
            )
        }

        @DisplayName("브랜드 필터와 명시 page/size를 적용한다.")
        @Test
        fun returnsProductPage_withBrandFilterAndPaging() {
            // arrange
            val brand = brandRepository.save(BrandModel(name = "Nike", description = "Shoes"))
            val otherBrand = brandRepository.save(BrandModel(name = "Adidas", description = "Shoes"))
            val first = saveProduct(brandId = brand.id, name = "First", likeCount = 30)
            saveProduct(brandId = brand.id, name = "Second", likeCount = 20)
            saveProduct(brandId = otherBrand.id, name = "Other", likeCount = 999)

            // act
            val response = getProducts("/api/v1/products?brandId=${brand.id}&sort=likes_desc&page=0&size=1")

            // assert
            val body = checkNotNull(response.body) { "response.body must not be null" }
            val data = checkNotNull(body.data) { "response.body.data must not be null" }
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(data.items.map { it.id }).containsExactly(first.id) },
                { assertThat(data.page).isEqualTo(0) },
                { assertThat(data.size).isEqualTo(1) },
                { assertThat(data.totalCount).isEqualTo(2) },
                { assertThat(data.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("page가 음수이면 표준 400 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenPageIsNegative() {
            // act
            val response = getProducts("/api/v1/products?page=-1")

            // assert
            val body = checkNotNull(response.body) { "response.body must not be null" }
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(body.meta.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
                { assertThat(body.meta.errorCode).isEqualTo(ErrorType.BAD_REQUEST.code) },
                { assertThat(body.meta.message).isEqualTo("페이지 번호는 0 이상이어야 합니다.") },
                { assertThat(body.data).isNull() },
            )
        }

        @DisplayName("size가 허용 범위를 벗어나면 표준 400 응답을 반환한다.")
        @Test
        fun returnsBadRequest_whenSizeIsOutOfRange() {
            // act
            val response = getProducts("/api/v1/products?size=101")

            // assert
            val body = checkNotNull(response.body) { "response.body must not be null" }
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(body.meta.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
                { assertThat(body.meta.errorCode).isEqualTo(ErrorType.BAD_REQUEST.code) },
                { assertThat(body.meta.message).isEqualTo("페이지 크기는 1 이상 100 이하여야 합니다.") },
                { assertThat(body.data).isNull() },
            )
        }
    }

    @DisplayName("GET /api/v1/products/{id}")
    @Nested
    inner class GetProductDetail {
        @DisplayName("상품 상세 조회 시 오늘 일간 랭킹 순위(1-based)가 함께 반환된다.")
        @Test
        fun returnsProductDetailWithRank() {
            val brand = brandRepository.save(BrandModel(name = "Nike", description = "Shoes"))
            val ranked = productRepository.save(ProductModel(brandId = brand.id, name = "ranked", description = "d", price = BigDecimal("10000.00")))
            val other = productRepository.save(ProductModel(brandId = brand.id, name = "other", description = "d", price = BigDecimal("10000.00")))
            val today = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            redisTemplate.opsForZSet().add("ranking:all:v1:$today", "${other.id}", 9.0)
            redisTemplate.opsForZSet().add("ranking:all:v1:$today", "${ranked.id}", 4.0)

            val response = testRestTemplate.exchange(
                "/api/v1/products/${ranked.id}",
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body!!.data!!.rank).isEqualTo(2L)
        }

        @DisplayName("랭킹에 없는 상품의 상세 조회는 rank=null을 반환한다.")
        @Test
        fun returnsNullRankWhenUnranked() {
            val brand = brandRepository.save(BrandModel(name = "Nike", description = "Shoes"))
            val product = productRepository.save(ProductModel(brandId = brand.id, name = "unranked", description = "d", price = BigDecimal("10000.00")))

            val response = testRestTemplate.exchange(
                "/api/v1/products/${product.id}",
                HttpMethod.GET,
                null,
                object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body!!.data!!.rank).isNull()
        }
    }

    private fun getProducts(url: String) = testRestTemplate.exchange(
        url,
        HttpMethod.GET,
        null,
        object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductPageResponse>>() {},
    )

    private fun saveProduct(
        brandId: Long,
        name: String,
        likeCount: Int,
        stockQuantity: Int = 0,
    ): ProductModel {
        val product = productRepository.save(
            ProductModel(
                brandId = brandId,
                name = name,
                description = "Description",
                price = BigDecimal("10000.00"),
                likeCount = likeCount,
            ),
        )
        productStockRepository.save(ProductStockModel(productId = product.id, quantity = stockQuantity))
        return product
    }
}
