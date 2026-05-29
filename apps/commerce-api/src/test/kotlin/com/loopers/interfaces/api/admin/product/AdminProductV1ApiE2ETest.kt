package com.loopers.interfaces.api.admin.product

import com.loopers.infrastructure.brand.BrandEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.productstat.ProductStatEntity
import com.loopers.infrastructure.productstat.ProductStatJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
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
class AdminProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStatJpaRepository: ProductStatJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    inner class GetProducts {
        @DisplayName("등록된 상품 목록을 페이지로 조회한다")
        @Test
        fun returnsProductPage() {
            val brand = createBrand(name = "loopers")
            val product = createProduct(brandId = brand.id, name = "loopers hoodie")
            productStatJpaRepository.save(ProductStatEntity(productId = product.id, likeCount = 3L))

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT?page=0&size=20",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<PageResponse<AdminProductV1Dto.ProductSummaryResponse>>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.data).hasSize(1) },
                { assertThat(response.body?.data?.data?.get(0)?.productId).isEqualTo(product.id) },
                { assertThat(response.body?.data?.data?.get(0)?.brandName).isEqualTo(brand.name) },
                { assertThat(response.body?.data?.data?.get(0)?.likeCount).isEqualTo(3L) },
            )
        }

        @DisplayName("브랜드 ID가 주어지면 해당 브랜드 상품만 조회한다")
        @Test
        fun returnsProductPageFilteredByBrandId() {
            val brand = createBrand(name = "loopers")
            val otherBrand = createBrand(name = "street")
            createProduct(brandId = brand.id, name = "loopers hoodie")
            createProduct(brandId = otherBrand.id, name = "street hoodie")

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT?page=0&size=20&brandId=${brand.id}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<PageResponse<AdminProductV1Dto.ProductSummaryResponse>>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.data).hasSize(1) },
                { assertThat(response.body?.data?.data?.get(0)?.brandId).isEqualTo(brand.id) },
            )
        }

        @DisplayName("관리자 식별 헤더가 없으면 상품 목록 조회에 실패한다")
        @Test
        fun returnsBadRequest_whenAdminHeaderIsMissing() {
            val response = testRestTemplate.exchange(
                PRODUCTS_ENDPOINT,
                HttpMethod.GET,
                HttpEntity.EMPTY,
                object : ParameterizedTypeReference<ApiResponse<PageResponse<AdminProductV1Dto.ProductSummaryResponse>>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

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
    ): ProductEntity {
        return productJpaRepository.save(
            ProductEntity(
                brandId = brandId,
                name = name,
                price = 10_000L,
                description = "$name product",
                imageUrl = "https://image.loopers/$name.png",
            ),
        )
    }

    private fun createAdminHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-Ldap", "admin")
        }
    }

    private companion object {
        private const val PRODUCTS_ENDPOINT = "/api-admin/v1/products"
    }
}
