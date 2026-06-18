package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.interfaces.api.product.ProductV1Dto
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
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
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body!!.data!!.items.map { it.id }).containsExactly(top.id, second.id, third.id) },
                { assertThat(response.body!!.data!!.items.first().stockQuantity).isEqualTo(9) },
                { assertThat(response.body!!.data!!.page).isEqualTo(0) },
                { assertThat(response.body!!.data!!.size).isEqualTo(20) },
                { assertThat(response.body!!.data!!.totalCount).isEqualTo(3) },
                { assertThat(response.body!!.data!!.totalPages).isEqualTo(1) },
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
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body!!.data!!.items.map { it.id }).containsExactly(first.id) },
                { assertThat(response.body!!.data!!.page).isEqualTo(0) },
                { assertThat(response.body!!.data!!.size).isEqualTo(1) },
                { assertThat(response.body!!.data!!.totalCount).isEqualTo(2) },
                { assertThat(response.body!!.data!!.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("page/size 범위가 유효하지 않으면 400을 반환한다.")
        @Test
        fun returnsBadRequest_whenPagingIsInvalid() {
            // act
            val response = getProducts("/api/v1/products?page=-1&size=101")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
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
