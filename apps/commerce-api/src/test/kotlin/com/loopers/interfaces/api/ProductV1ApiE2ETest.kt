package com.loopers.interfaces.api

import com.loopers.application.product.CreateProductCommand
import com.loopers.application.product.ProductFacade
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
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
import org.springframework.http.MediaType

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val productFacade: ProductFacade,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val PRODUCT_ENDPOINT = "/api/v1/products"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun getProduct(id: Long): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            "$PRODUCT_ENDPOINT/$id",
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            responseType,
        )
    }

    @DisplayName("GET /api/v1/products/{id}")
    @Nested
    inner class GetProductDetail {

        @DisplayName("존재하는 상품 id로 조회하면, brandName/stockQuantity/likeCount를 포함해 반환한다.")
        @Test
        fun returnsProductDetail_whenExists() {
            // arrange
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "Just do it"))
            val detail = productFacade.createProduct(
                CreateProductCommand(name = "에어맥스", price = 100000L, description = "d", brandId = brand.id, quantity = 30),
            )

            // act
            val response = getProduct(detail.id)

            // assert
            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("id")).isEqualTo(detail.id.toInt()) },
                { assertThat(data?.get("name")).isEqualTo("에어맥스") },
                { assertThat((data?.get("price") as? Number)?.toLong()).isEqualTo(100000L) },
                { assertThat(data?.get("brandId")).isEqualTo(brand.id.toInt()) },
                { assertThat(data?.get("brandName")).isEqualTo("Nike") },
                { assertThat(data?.get("stockQuantity")).isEqualTo(30) },
                { assertThat((data?.get("likeCount") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }

        @DisplayName("존재하지 않는 상품 id로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = getProduct(9999L)
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품 id로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenDeleted() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val detail = productFacade.createProduct(
                CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand.id, quantity = 5),
            )
            productFacade.deleteProduct(detail.id)

            val response = getProduct(detail.id)
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
