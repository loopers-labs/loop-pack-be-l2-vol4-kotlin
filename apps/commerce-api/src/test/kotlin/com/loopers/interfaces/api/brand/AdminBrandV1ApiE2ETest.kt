package com.loopers.interfaces.api.brand

import com.loopers.infrastructure.brand.BrandEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductEntity
import com.loopers.infrastructure.product.ProductJpaRepository
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.data.repository.findByIdOrNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminBrandV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    inner class CreateBrand {
        @DisplayName("브랜드 등록 요청이 유효하면 브랜드 등록에 성공한다")
        @Test
        fun returnsSuccess_whenCreateBrandRequestIsValid() {
            val request = createBrandRequest()

            val response = testRestTemplate.exchange(
                CREATE_BRAND_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.brandId).isNotNull() },
                { assertThat(response.body?.data?.name).isEqualTo(request.name) },
                { assertThat(brandJpaRepository.findAll()).hasSize(1) },
            )
        }

        @DisplayName("관리자 식별 헤더가 없으면 브랜드 등록에 실패한다")
        @Test
        fun returnsBadRequest_whenAdminHeaderIsMissing() {
            val response = testRestTemplate.exchange(
                CREATE_BRAND_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(createBrandRequest()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("이미 존재하는 브랜드명으로 브랜드 등록 요청 시 실패한다")
        @Test
        fun returnsConflict_whenBrandNameAlreadyExists() {
            val request = createBrandRequest()
            testRestTemplate.exchange(
                CREATE_BRAND_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            val response = testRestTemplate.exchange(
                CREATE_BRAND_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(brandJpaRepository.findAll()).hasSize(1) },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    inner class GetBrands {
        @DisplayName("등록된 브랜드 목록을 페이지로 조회한다")
        @Test
        fun returnsBrandPage() {
            createBrand(name = "loopers")
            createBrand(name = "street")

            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT?page=0&size=20",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<PageResponse<AdminBrandV1Dto.BrandResponse>>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.data).hasSize(2) },
                { assertThat(response.body?.data?.meta?.totalElements).isEqualTo(2L) },
                { assertThat(response.body?.data?.data?.map { it.name }).containsExactly("street", "loopers") },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    inner class GetBrand {
        @DisplayName("등록된 브랜드 상세 정보를 조회한다")
        @Test
        fun returnsBrand() {
            val brandId = createBrand(name = "loopers")

            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT/$brandId",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.brandId).isEqualTo(brandId) },
                { assertThat(response.body?.data?.name).isEqualTo("loopers") },
            )
        }

        @DisplayName("존재하지 않는 브랜드 상세 조회 요청 시 실패한다")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT/999",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드 상세 조회 요청 시 실패한다")
        @Test
        fun returnsNotFound_whenBrandIsDeleted() {
            val brand = brandJpaRepository.save(
                BrandEntity(
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                    isDeleted = true,
                ),
            )

            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    inner class UpdateBrand {
        @DisplayName("브랜드 수정 요청이 유효하면 브랜드 정보를 수정한다")
        @Test
        fun returnsUpdatedBrand_whenRequestIsValid() {
            val brandId = createBrand(name = "loopers")
            val request = updateBrandRequest(
                name = "loopers updated",
                description = "updated brand",
                logoImageUrl = "https://image.loopers/updated.png",
            )

            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT/$brandId",
                HttpMethod.PUT,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.brandId).isEqualTo(brandId) },
                { assertThat(response.body?.data?.name).isEqualTo(request.name) },
                { assertThat(response.body?.data?.description).isEqualTo(request.description) },
                { assertThat(response.body?.data?.logoImageUrl).isEqualTo(request.logoImageUrl) },
            )
        }

        @DisplayName("존재하지 않는 브랜드 수정 요청 시 실패한다")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT/999",
                HttpMethod.PUT,
                HttpEntity(updateBrandRequest(), createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드 수정 요청 시 실패한다")
        @Test
        fun returnsNotFound_whenBrandIsDeleted() {
            val brand = brandJpaRepository.save(
                BrandEntity(
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                    isDeleted = true,
                ),
            )

            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT/${brand.id}",
                HttpMethod.PUT,
                HttpEntity(updateBrandRequest(), createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("이미 존재하는 브랜드명으로 브랜드 수정 요청 시 실패한다")
        @Test
        fun returnsConflict_whenBrandNameAlreadyExists() {
            val brandId = createBrand(name = "loopers")
            createBrand(name = "street")

            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT/$brandId",
                HttpMethod.PUT,
                HttpEntity(updateBrandRequest(name = "street"), createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    inner class DeleteBrand {
        @DisplayName("브랜드 삭제 요청이 유효하면 브랜드와 해당 브랜드 상품을 삭제한다")
        @Test
        fun deletesBrandAndProducts() {
            val brandId = createBrand(name = "loopers")
            val otherBrandId = createBrand(name = "street")
            val product = createProduct(brandId = brandId)
            val otherProduct = createProduct(brandId = otherBrandId)

            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT/$brandId",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(isBrandDeleted(brandId)).isTrue() },
                { assertThat(isProductDeleted(product.id)).isTrue() },
                { assertThat(productJpaRepository.findByIdOrNull(otherProduct.id)?.isDeleted).isFalse() },
            )
        }

        @DisplayName("존재하지 않는 브랜드 삭제 요청 시 실패한다")
        @Test
        fun returnsNotFound_whenBrandDoesNotExist() {
            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT/999",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드 삭제 요청 시 실패한다")
        @Test
        fun returnsNotFound_whenBrandIsDeleted() {
            val brand = brandJpaRepository.save(
                BrandEntity(
                    name = "loopers",
                    description = "loopers brand",
                    logoImageUrl = "https://image.loopers/logo.png",
                    isDeleted = true,
                ),
            )

            val response = testRestTemplate.exchange(
                "$BRANDS_ENDPOINT/${brand.id}",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    private fun createBrandRequest(
        name: String = "loopers",
        description: String = "loopers brand",
        logoImageUrl: String = "https://image.loopers/logo.png",
    ): AdminBrandV1Dto.CreateBrandRequest {
        return AdminBrandV1Dto.CreateBrandRequest(
            name = name,
            description = description,
            logoImageUrl = logoImageUrl,
        )
    }

    private fun updateBrandRequest(
        name: String = "loopers updated",
        description: String = "updated brand",
        logoImageUrl: String = "https://image.loopers/updated.png",
    ): AdminBrandV1Dto.UpdateBrandRequest {
        return AdminBrandV1Dto.UpdateBrandRequest(
            name = name,
            description = description,
            logoImageUrl = logoImageUrl,
        )
    }

    private fun createAdminHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-Ldap", "loopers.admin")
        }
    }

    private fun createBrand(name: String): Long {
        val response = testRestTemplate.exchange(
            BRANDS_ENDPOINT,
            HttpMethod.POST,
            HttpEntity(createBrandRequest(name = name), createAdminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
        )

        return response.body?.data?.brandId ?: throw IllegalStateException("Brand creation failed.")
    }

    private fun createProduct(brandId: Long): ProductEntity {
        return productJpaRepository.save(
            ProductEntity(
                brandId = brandId,
                name = "loopers hoodie $brandId",
                price = 10_000L,
                description = "loopers product",
                imageUrl = "https://image.loopers/product.png",
            ),
        )
    }

    private fun isBrandDeleted(brandId: Long): Boolean {
        return jdbcTemplate.queryForObject(
            "select is_deleted from brand where id = ?",
            Boolean::class.java,
            brandId,
        ) ?: false
    }

    private fun isProductDeleted(productId: Long): Boolean {
        return jdbcTemplate.queryForObject(
            "select is_deleted from product where id = ?",
            Boolean::class.java,
            productId,
        ) ?: false
    }

    private companion object {
        private const val BRANDS_ENDPOINT = "/api-admin/v1/brands"
        private const val CREATE_BRAND_ENDPOINT = BRANDS_ENDPOINT
    }
}
