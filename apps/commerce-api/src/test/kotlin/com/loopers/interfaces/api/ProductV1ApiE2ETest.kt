package com.loopers.interfaces.api

import com.loopers.infrastructure.brand.BrandJpaEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.query.product.ProductLikeCountProjectionEntity
import com.loopers.query.product.ProductLikeCountQueryRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val productLikeCountQueryRepository: ProductLikeCountQueryRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ENDPOINT = "/api/v1/products"
        private val ENDPOINT_WITH_ID: (Long) -> String = { id: Long -> "$ENDPOINT/$id" }
    }

    private lateinit var brand: BrandJpaEntity

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
    }

    private fun createProductWithStock(
        brandId: Long,
        name: String,
        price: Long,
        stock: Int,
        likeCount: Int = 0,
    ): ProductJpaEntity {
        val product = productJpaRepository.save(
            ProductJpaEntity(
                brandId = brandId,
                name = name,
                description = "$name 설명",
                price = price,
            ),
        )
        stockJpaRepository.save(StockJpaEntity(productId = product.id, quantity = stock))
        productLikeCountQueryRepository.save(
            ProductLikeCountProjectionEntity(
                productId = product.id,
                brandId = brandId,
                likeCount = likeCount,
            ),
        )
        return product
    }

    @DisplayName("POST /api/v1/products")
    @Nested
    inner class CreateProduct {
        @DisplayName("유효한 요청이면 상품을 생성한다.")
        @Test
        fun createsProduct_whenRequestIsValid() {
            // arrange
            val request = ProductV1Dto.CreateRequest(
                brandId = brand.id,
                name = "테스트 상품",
                description = "상품 설명",
                price = 10000,
                initialStock = 100,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED) },
                { assertThat(response.body?.data?.name).isEqualTo("테스트 상품") },
                { assertThat(response.body?.data?.price).isEqualTo(10000) },
                { assertThat(response.body?.data?.stock).isEqualTo(100) },
                { assertThat(response.body?.data?.likeCount).isEqualTo(0) },
                { assertThat(response.body?.data?.soldOut).isFalse() },
            )
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    inner class GetProduct {
        @DisplayName("존재하는 상품 ID이면 상품 상세 정보를 반환한다.")
        @Test
        fun returnsProductDetail_whenProductExists() {
            // arrange
            val product = createProductWithStock(
                brandId = brand.id,
                name = "테스트 상품",
                price = 15000,
                stock = 50,
                likeCount = 10,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_WITH_ID(product.id),
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(product.id) },
                { assertThat(response.body?.data?.name).isEqualTo("테스트 상품") },
                { assertThat(response.body?.data?.brandName).isEqualTo("TestBrand") },
                { assertThat(response.body?.data?.price).isEqualTo(15000) },
                { assertThat(response.body?.data?.stock).isEqualTo(50) },
                { assertThat(response.body?.data?.likeCount).isEqualTo(10) },
            )
        }

        @DisplayName("존재하지 않는 상품 ID이면 404를 반환한다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_WITH_ID(999L),
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    inner class GetProducts {
        @DisplayName("상품 목록을 페이징하여 반환한다.")
        @Test
        fun returnsPagedProducts() {
            // arrange
            repeat(3) { i ->
                createProductWithStock(
                    brandId = brand.id,
                    name = "상품$i",
                    price = 10000L + i * 1000,
                    stock = 10,
                )
            }

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>>() {}
            val response = testRestTemplate.exchange(
                "$ENDPOINT?page=0&size=2",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.items).hasSize(2) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(3) },
                { assertThat(response.body?.data?.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("브랜드 ID로 필터링한다.")
        @Test
        fun filtersProductsByBrandId() {
            // arrange
            val anotherBrand = brandJpaRepository.save(
                BrandJpaEntity(name = "OtherBrand", description = "다른 브랜드", logoImageUrl = null),
            )
            createProductWithStock(brandId = brand.id, name = "내 브랜드 상품", price = 10000, stock = 10)
            createProductWithStock(brandId = anotherBrand.id, name = "다른 브랜드 상품", price = 20000, stock = 5)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>>() {}
            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=${brand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.items).hasSize(1) },
                { assertThat(response.body?.data?.items?.first()?.name).isEqualTo("내 브랜드 상품") },
            )
        }

        @DisplayName("좋아요 순 정렬로 조회한다.")
        @Test
        fun sortsProductsByLikesDesc() {
            // arrange
            createProductWithStock(brandId = brand.id, name = "인기 상품", price = 10000, stock = 10, likeCount = 100)
            createProductWithStock(brandId = brand.id, name = "보통 상품", price = 20000, stock = 5, likeCount = 10)
            createProductWithStock(brandId = brand.id, name = "비인기 상품", price = 5000, stock = 20, likeCount = 1)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<PageResponse<ProductV1Dto.ProductSummaryResponse>>>() {}
            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=${brand.id}&sort=LIKES_DESC",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            // assert
            val items = response.body?.data?.items!!
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(items).hasSize(3) },
                { assertThat(items[0].name).isEqualTo("인기 상품") },
                { assertThat(items[1].name).isEqualTo("보통 상품") },
                { assertThat(items[2].name).isEqualTo("비인기 상품") },
            )
        }
    }

    @DisplayName("PUT /api/v1/products/{productId}")
    @Nested
    inner class UpdateProduct {
        @DisplayName("유효한 요청이면 상품을 수정한다.")
        @Test
        fun updatesProduct_whenRequestIsValid() {
            // arrange
            val product = createProductWithStock(
                brandId = brand.id,
                name = "원래 이름",
                price = 10000,
                stock = 50,
            )
            val request = ProductV1Dto.UpdateRequest(
                name = "수정된 이름",
                description = "수정된 설명",
                price = 20000,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_WITH_ID(product.id),
                HttpMethod.PUT,
                HttpEntity(request),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("수정된 이름") },
                { assertThat(response.body?.data?.description).isEqualTo("수정된 설명") },
                { assertThat(response.body?.data?.price).isEqualTo(20000) },
            )
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}")
    @Nested
    inner class DeleteProduct {
        @DisplayName("존재하는 상품 ID이면 204를 반환한다.")
        @Test
        fun deletesProduct_whenProductExists() {
            // arrange
            val product = createProductWithStock(
                brandId = brand.id,
                name = "삭제할 상품",
                price = 10000,
                stock = 10,
            )

            // act
            val response = testRestTemplate.exchange(
                ENDPOINT_WITH_ID(product.id),
                HttpMethod.DELETE,
                HttpEntity<Any>(Unit),
                Unit::class.java,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NO_CONTENT)
        }
    }
}
