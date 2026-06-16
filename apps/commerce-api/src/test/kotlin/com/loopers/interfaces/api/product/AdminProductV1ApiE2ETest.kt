package com.loopers.interfaces.api.product

import com.loopers.infrastructure.brand.entity.BrandEntity
import com.loopers.infrastructure.brand.repository.BrandJpaRepository
import com.loopers.infrastructure.inventory.entity.InventoryEntity
import com.loopers.infrastructure.inventory.repository.InventoryJpaRepository
import com.loopers.infrastructure.product.entity.ProductEntity
import com.loopers.infrastructure.product.entity.ProductStatEntity
import com.loopers.infrastructure.product.repository.ProductJpaRepository
import com.loopers.infrastructure.product.repository.ProductStatJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.product.dto.AdminProductV1Dto
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
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val inventoryJpaRepository: InventoryJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStatJpaRepository: ProductStatJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
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

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    inner class CreateProduct {
        @DisplayName("기존 브랜드에 상품을 등록한다")
        @Test
        fun createsProduct() {
            val brand = createBrand(name = "loopers")
            val request = AdminProductV1Dto.CreateProductRequest(
                brandId = brand.id,
                name = "loopers hoodie",
                price = 10_000L,
                description = "loopers product",
                imageUrl = "https://image.loopers/product.png",
                quantity = 100L,
            )

            val response = testRestTemplate.exchange(
                PRODUCTS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.productId).isPositive() },
                { assertThat(response.body?.data?.productName).isEqualTo("loopers hoodie") },
                { assertThat(response.body?.data?.brand?.brandId).isEqualTo(brand.id) },
                { assertThat(response.body?.data?.likeCount).isEqualTo(0L) },
                { assertThat(response.body?.data?.quantity).isEqualTo(100L) },
                { assertThat(productJpaRepository.findAll()).hasSize(1) },
                { assertThat(inventoryJpaRepository.findAll()).hasSize(1) },
            )
        }

        @DisplayName("같은 브랜드에 같은 이름의 상품은 등록할 수 없다")
        @Test
        fun returnsConflict_whenProductNameAlreadyExistsInBrand() {
            val brand = createBrand(name = "loopers")
            createProduct(brandId = brand.id, name = "loopers hoodie")
            val request = AdminProductV1Dto.CreateProductRequest(
                brandId = brand.id,
                name = "loopers hoodie",
                price = 10_000L,
                description = "loopers product",
                imageUrl = "https://image.loopers/product.png",
                quantity = 100L,
            )

            val response = testRestTemplate.exchange(
                PRODUCTS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("존재하지 않는 브랜드에는 상품을 등록할 수 없다")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            val request = AdminProductV1Dto.CreateProductRequest(
                brandId = 999L,
                name = "loopers hoodie",
                price = 10_000L,
                description = "loopers product",
                imageUrl = "https://image.loopers/product.png",
                quantity = 100L,
            )

            val response = testRestTemplate.exchange(
                PRODUCTS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드에는 상품을 등록할 수 없다")
        @Test
        fun returnsNotFound_whenBrandIsDeleted() {
            val brand = createBrand(name = "loopers", isDeleted = true)
            val request = AdminProductV1Dto.CreateProductRequest(
                brandId = brand.id,
                name = "loopers hoodie",
                price = 10_000L,
                description = "loopers product",
                imageUrl = "https://image.loopers/product.png",
                quantity = 100L,
            )

            val response = testRestTemplate.exchange(
                PRODUCTS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    inner class UpdateProduct {
        @DisplayName("등록된 상품 기본 정보를 수정한다")
        @Test
        fun updatesProduct() {
            val brand = createBrand(name = "loopers")
            val product = createProduct(brandId = brand.id, name = "loopers hoodie")
            createInventory(productId = product.id, quantity = 7L)
            productStatJpaRepository.save(ProductStatEntity(productId = product.id, likeCount = 3L))
            val request = AdminProductV1Dto.UpdateProductRequest(
                name = "updated hoodie",
                price = 20_000L,
                description = "updated product",
                imageUrl = "https://image.loopers/updated.png",
            )

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}",
                HttpMethod.PUT,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            val updatedProduct = productJpaRepository.findById(product.id).orElseThrow()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.productId).isEqualTo(product.id) },
                { assertThat(response.body?.data?.productName).isEqualTo("updated hoodie") },
                { assertThat(response.body?.data?.price).isEqualTo(20_000L) },
                { assertThat(response.body?.data?.brand?.brandId).isEqualTo(brand.id) },
                { assertThat(response.body?.data?.likeCount).isEqualTo(3L) },
                { assertThat(response.body?.data?.quantity).isEqualTo(7L) },
                { assertThat(updatedProduct.brandId).isEqualTo(brand.id) },
            )
        }

        @DisplayName("같은 브랜드 내 다른 상품 이름과 중복되게 수정할 수 없다")
        @Test
        fun returnsConflict_whenProductNameAlreadyExistsInBrand() {
            val brand = createBrand(name = "loopers")
            val product = createProduct(brandId = brand.id, name = "loopers hoodie")
            createProduct(brandId = brand.id, name = "updated hoodie")
            createInventory(productId = product.id, quantity = 7L)
            val request = AdminProductV1Dto.UpdateProductRequest(
                name = "updated hoodie",
                price = 20_000L,
                description = "updated product",
                imageUrl = "https://image.loopers/updated.png",
            )

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}",
                HttpMethod.PUT,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("존재하지 않는 상품은 수정할 수 없다")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            val request = AdminProductV1Dto.UpdateProductRequest(
                name = "updated hoodie",
                price = 20_000L,
                description = "updated product",
                imageUrl = "https://image.loopers/updated.png",
            )

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/999",
                HttpMethod.PUT,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 수정할 수 없다")
        @Test
        fun returnsNotFound_whenProductIsDeleted() {
            val brand = createBrand(name = "loopers")
            val product = createProduct(brandId = brand.id, name = "loopers hoodie", isDeleted = true)
            val request = AdminProductV1Dto.UpdateProductRequest(
                name = "updated hoodie",
                price = 20_000L,
                description = "updated product",
                imageUrl = "https://image.loopers/updated.png",
            )

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}",
                HttpMethod.PUT,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드의 상품은 수정할 수 없다")
        @Test
        fun returnsNotFound_whenBrandIsDeleted() {
            val brand = createBrand(name = "loopers", isDeleted = true)
            val product = createProduct(brandId = brand.id, name = "loopers hoodie")
            val request = AdminProductV1Dto.UpdateProductRequest(
                name = "updated hoodie",
                price = 20_000L,
                description = "updated product",
                imageUrl = "https://image.loopers/updated.png",
            )

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}",
                HttpMethod.PUT,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    inner class DeleteProduct {
        @DisplayName("등록된 상품을 삭제한다")
        @Test
        fun deletesProduct() {
            val brand = createBrand(name = "loopers")
            val product = createProduct(brandId = brand.id, name = "loopers hoodie")

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(isProductDeleted(product.id)).isTrue() },
            )
        }

        @DisplayName("존재하지 않는 상품은 삭제할 수 없다")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/999",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("이미 삭제된 상품은 삭제할 수 없다")
        @Test
        fun returnsNotFound_whenProductIsDeleted() {
            val brand = createBrand(name = "loopers")
            val product = createProduct(brandId = brand.id, name = "loopers hoodie", isDeleted = true)

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드의 상품은 삭제할 수 없다")
        @Test
        fun returnsNotFound_whenBrandIsDeleted() {
            val brand = createBrand(name = "loopers", isDeleted = true)
            val product = createProduct(brandId = brand.id, name = "loopers hoodie")

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    inner class GetProduct {
        @DisplayName("등록된 상품 상세 정보를 조회한다")
        @Test
        fun returnsProductDetail() {
            val brand = createBrand(name = "loopers")
            val product = createProduct(brandId = brand.id, name = "loopers hoodie")
            createInventory(productId = product.id, quantity = 7L)
            productStatJpaRepository.save(ProductStatEntity(productId = product.id, likeCount = 3L))

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.productId).isEqualTo(product.id) },
                { assertThat(response.body?.data?.productName).isEqualTo(product.name) },
                { assertThat(response.body?.data?.brand?.brandId).isEqualTo(brand.id) },
                { assertThat(response.body?.data?.brand?.name).isEqualTo(brand.name) },
                { assertThat(response.body?.data?.likeCount).isEqualTo(3L) },
                { assertThat(response.body?.data?.quantity).isEqualTo(7L) },
            )
        }

        @DisplayName("존재하지 않는 상품은 조회할 수 없다")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/999",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 조회할 수 없다")
        @Test
        fun returnsNotFound_whenProductIsDeleted() {
            val brand = createBrand(name = "loopers")
            val product = createProduct(brandId = brand.id, name = "loopers hoodie", isDeleted = true)

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드의 상품은 조회할 수 없다")
        @Test
        fun returnsNotFound_whenBrandIsDeleted() {
            val brand = createBrand(name = "loopers", isDeleted = true)
            val product = createProduct(brandId = brand.id, name = "loopers hoodie")

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductDetailResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    private fun createBrand(
        name: String,
        isDeleted: Boolean = false,
    ): BrandEntity {
        return brandJpaRepository.save(
            BrandEntity(
                name = name,
                description = "$name brand",
                logoImageUrl = "https://image.loopers/$name.png",
                isDeleted = isDeleted,
            ),
        )
    }

    private fun createProduct(
        brandId: Long,
        name: String,
        isDeleted: Boolean = false,
    ): ProductEntity {
        return productJpaRepository.save(
            ProductEntity(
                brandId = brandId,
                name = name,
                price = 10_000L,
                description = "$name product",
                imageUrl = "https://image.loopers/$name.png",
                isDeleted = isDeleted,
            ),
        )
    }

    private fun createInventory(productId: Long, quantity: Long): InventoryEntity {
        return inventoryJpaRepository.save(
            InventoryEntity(
                productId = productId,
                quantity = quantity,
            ),
        )
    }

    private fun createAdminHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-Ldap", "loopers.admin")
            contentType = MediaType.APPLICATION_JSON
        }
    }

    private fun isProductDeleted(productId: Long): Boolean {
        return jdbcTemplate.queryForObject(
            "select is_deleted from product where id = ?",
            Boolean::class.java,
            productId,
        ) ?: false
    }

    private companion object {
        private const val PRODUCTS_ENDPOINT = "/api-admin/v1/products"
    }
}
