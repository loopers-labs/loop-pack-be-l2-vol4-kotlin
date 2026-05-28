package com.loopers.interfaces.api.admin.brand

import com.loopers.infrastructure.brand.BrandJpaRepository
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
class AdminBrandV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
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

    private fun createAdminHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-Ldap", "admin")
        }
    }

    private fun createBrand(name: String) {
        testRestTemplate.exchange(
            BRANDS_ENDPOINT,
            HttpMethod.POST,
            HttpEntity(createBrandRequest(name = name), createAdminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.BrandResponse>>() {},
        )
    }

    private companion object {
        private const val BRANDS_ENDPOINT = "/api-admin/v1/brands"
        private const val CREATE_BRAND_ENDPOINT = BRANDS_ENDPOINT
    }
}
