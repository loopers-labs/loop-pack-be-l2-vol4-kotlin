package com.loopers.interfaces.api.catalog

import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.like.LikeV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
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
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("관리자는 브랜드와 상품을 생성하고 소비자는 상품 목록과 상세를 조회한다.")
    @Test
    fun createsCatalogAndReadsProducts() {
        userJpaRepository.save(
            User(
                loginId = "admin01",
                encryptedPassword = passwordEncoder.encode(RawPassword("abcd1234")),
                name = "Admin",
                birthdate = LocalDate.of(1990, 1, 1),
                email = "admin@example.com",
                role = UserRole.ADMIN,
            ),
        )
        val headers = HttpHeaders().apply {
            add("X-Loopers-LoginId", "admin01")
            add("X-Loopers-LoginPw", "abcd1234")
        }
        val brandType = object : ParameterizedTypeReference<ApiResponse<CatalogV1Dto.BrandResponse>>() {}
        val productType = object : ParameterizedTypeReference<ApiResponse<CatalogV1Dto.ProductResponse>>() {}
        val listType = object : ParameterizedTypeReference<ApiResponse<List<CatalogV1Dto.ProductDisplayResponse>>>() {}
        val detailType = object : ParameterizedTypeReference<ApiResponse<CatalogV1Dto.ProductDetailResponse>>() {}

        val brandResponse = testRestTemplate.exchange(
            "/api/v1/admin/brands",
            HttpMethod.POST,
            HttpEntity(mapOf("name" to "Nike"), headers),
            brandType,
        )
        val productResponse = testRestTemplate.exchange(
            "/api/v1/admin/products",
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "brandId" to brandResponse.body?.data?.brandId,
                    "name" to "Air Max",
                    "price" to 129000,
                    "initialStock" to 2,
                    "detailImageUrls" to listOf("https://cdn.example.com/air-max.png"),
                ),
                headers,
            ),
            productType,
        )

        val listResponse = testRestTemplate.exchange(
            "/api/v1/products?sort=latest&page=0&size=20",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            listType,
        )
        val detailResponse = testRestTemplate.exchange(
            "/api/v1/products/${productResponse.body?.data?.productId}",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            detailType,
        )

        assertAll(
            { assertThat(brandResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(productResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(listResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(listResponse.body?.data).hasSize(1) },
            { assertThat(listResponse.body?.data?.first()?.productName).isEqualTo("Air Max") },
            { assertThat(listResponse.body?.data?.first()?.soldOut).isFalse() },
            { assertThat(detailResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(detailResponse.body?.data?.detailImages).containsExactly("https://cdn.example.com/air-max.png") },
        )
    }

    @DisplayName("소비자는 선택한 브랜드 상품을 좋아요 많은 순으로 조회한다.")
    @Test
    fun readsBrandProductsSortedByLikesDesc() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        saveUser("consumer02")
        val nike = createBrand("Nike")
        val adidas = createBrand("Adidas")
        val low = createProduct(nike.brandId, "Low", 1000, 1)
        val high = createProduct(nike.brandId, "High", 2000, 1)
        val otherBrand = createProduct(adidas.brandId, "Other", 3000, 1)
        registerLike("consumer01", low.productId)
        registerLike("consumer01", high.productId)
        registerLike("consumer02", high.productId)
        registerLike("consumer01", otherBrand.productId)
        registerLike("consumer02", otherBrand.productId)
        val listType = object : ParameterizedTypeReference<ApiResponse<List<CatalogV1Dto.ProductDisplayResponse>>>() {}

        val response = testRestTemplate.exchange(
            "/api/v1/brands/${nike.brandId}/products?sort=likes_desc&page=0&size=20",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            listType,
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.map { it.productId }).containsExactly(high.productId, low.productId) },
            { assertThat(response.body?.data?.map { it.brandId }).containsOnly(nike.brandId) },
            { assertThat(response.body?.data?.map { it.likeCount }).containsExactly(2L, 1L) },
        )
    }

    private fun saveUser(loginId: String, role: UserRole = UserRole.CONSUMER): User =
        userJpaRepository.save(
            User(
                loginId = loginId,
                encryptedPassword = passwordEncoder.encode(RawPassword("abcd1234")),
                name = loginId,
                birthdate = LocalDate.of(1990, 1, 1),
                email = "$loginId@example.com",
                role = role,
            ),
        )

    private fun createBrand(name: String): CatalogV1Dto.BrandResponse =
        testRestTemplate.exchange(
            "/api/v1/admin/brands",
            HttpMethod.POST,
            HttpEntity(mapOf("name" to name), authHeaders("admin01")),
            object : ParameterizedTypeReference<ApiResponse<CatalogV1Dto.BrandResponse>>() {},
        ).body?.data ?: error("Brand creation failed")

    private fun createProduct(
        brandId: Long,
        name: String,
        price: Long,
        stock: Int,
    ): CatalogV1Dto.ProductResponse =
        testRestTemplate.exchange(
            "/api/v1/admin/products",
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "brandId" to brandId,
                    "name" to name,
                    "price" to price,
                    "initialStock" to stock,
                    "detailImageUrls" to emptyList<String>(),
                ),
                authHeaders("admin01"),
            ),
            object : ParameterizedTypeReference<ApiResponse<CatalogV1Dto.ProductResponse>>() {},
        ).body?.data ?: error("Product creation failed")

    private fun registerLike(loginId: String, productId: Long) {
        testRestTemplate.exchange(
            "/api/v1/products/$productId/likes",
            HttpMethod.POST,
            HttpEntity<Any>(null, authHeaders(loginId)),
            object : ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>>() {},
        )
    }

    private fun authHeaders(loginId: String): HttpHeaders =
        HttpHeaders().apply {
            add("X-Loopers-LoginId", loginId)
            add("X-Loopers-LoginPw", "abcd1234")
        }
}
