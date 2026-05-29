package com.loopers.interfaces.api.like

import com.loopers.infrastructure.brand.BrandEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.ProductLikeEntity
import com.loopers.infrastructure.like.ProductLikeJpaRepository
import com.loopers.infrastructure.product.ProductEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.productstat.ProductStatEntity
import com.loopers.infrastructure.productstat.ProductStatJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productLikeJpaRepository: ProductLikeJpaRepository,
    private val productStatJpaRepository: ProductStatJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    inner class LikeProduct {
        @DisplayName("상품을 좋아요 상태로 만든다")
        @Test
        fun likesProduct() {
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createUserHeaders(memberId = 1L)),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            val productStat = productStatJpaRepository.findByProductId(product.id)
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(productLikeJpaRepository.findAll()).hasSize(1) },
                { assertThat(productStat?.likeCount).isEqualTo(1L) },
            )
        }

        @DisplayName("이미 좋아요 상태여도 성공하고 좋아요 수는 증가하지 않는다")
        @Test
        fun ignoresDuplicateLike() {
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            productStatJpaRepository.save(ProductStatEntity(productId = product.id, likeCount = 0L))

            testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createUserHeaders(memberId = 1L)),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )
            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createUserHeaders(memberId = 1L)),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            val productStat = productStatJpaRepository.findByProductId(product.id)
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(productLikeJpaRepository.findAll()).hasSize(1) },
                { assertThat(productStat?.likeCount).isEqualTo(1L) },
            )
        }

        @DisplayName("존재하지 않는 상품은 좋아요할 수 없다")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/999/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createUserHeaders(memberId = 1L)),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 좋아요할 수 없다")
        @Test
        fun returnsNotFound_whenProductIsDeleted() {
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, isDeleted = true)

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createUserHeaders(memberId = 1L)),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    inner class UnlikeProduct {
        @DisplayName("상품을 좋아요하지 않은 상태로 만든다")
        @Test
        fun unlikesProduct() {
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            productLikeJpaRepository.save(ProductLikeEntity(memberId = 1L, productId = product.id))
            productStatJpaRepository.save(ProductStatEntity(productId = product.id, likeCount = 1L))

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createUserHeaders(memberId = 1L)),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            val productStat = productStatJpaRepository.findByProductId(product.id)
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(productLikeJpaRepository.findAll()).isEmpty() },
                { assertThat(productStat?.likeCount).isEqualTo(0L) },
            )
        }

        @DisplayName("이미 좋아요하지 않은 상태여도 성공하고 좋아요 수는 감소하지 않는다")
        @Test
        fun ignoresAbsentLike() {
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            productStatJpaRepository.save(ProductStatEntity(productId = product.id, likeCount = 1L))

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createUserHeaders(memberId = 1L)),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            val productStat = productStatJpaRepository.findByProductId(product.id)
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(productLikeJpaRepository.findAll()).isEmpty() },
                { assertThat(productStat?.likeCount).isEqualTo(1L) },
            )
        }

        @DisplayName("존재하지 않는 상품은 좋아요 취소할 수 없다")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/999/likes",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createUserHeaders(memberId = 1L)),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 좋아요 취소할 수 없다")
        @Test
        fun returnsNotFound_whenProductIsDeleted() {
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, isDeleted = true)

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createUserHeaders(memberId = 1L)),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    private fun createBrand(): BrandEntity {
        return brandJpaRepository.save(
            BrandEntity(
                name = "loopers",
                description = "loopers brand",
                logoImageUrl = "https://image.loopers/brand.png",
            ),
        )
    }

    private fun createProduct(
        brandId: Long,
        isDeleted: Boolean = false,
    ): ProductEntity {
        return productJpaRepository.save(
            ProductEntity(
                brandId = brandId,
                name = "loopers hoodie",
                price = 10_000L,
                description = "loopers product",
                imageUrl = "https://image.loopers/product.png",
                isDeleted = isDeleted,
            ),
        )
    }

    private fun createUserHeaders(memberId: Long): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-User-Id", memberId.toString())
        }
    }

    private companion object {
        private const val PRODUCTS_ENDPOINT = "/api/v1/products"
    }
}
