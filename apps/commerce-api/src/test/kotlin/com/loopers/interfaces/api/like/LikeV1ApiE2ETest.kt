package com.loopers.interfaces.api.like

import com.loopers.infrastructure.brand.BrandEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.like.ProductLikeEntity
import com.loopers.infrastructure.like.ProductLikeJpaRepository
import com.loopers.infrastructure.member.MemberEntity
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.infrastructure.product.ProductEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.productstat.ProductStatEntity
import com.loopers.infrastructure.productstat.ProductStatJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.domain.user.PasswordEncoder
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
    private val memberJpaRepository: MemberJpaRepository,
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
            val member = createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            val productStat = productStatJpaRepository.findByProductId(product.id)
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(productLikeJpaRepository.findAll().single().memberId).isEqualTo(member.id) },
                { assertThat(productStat?.likeCount).isEqualTo(1L) },
            )
        }

        @DisplayName("이미 좋아요 상태여도 성공하고 좋아요 수는 증가하지 않는다")
        @Test
        fun ignoresDuplicateLike() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            productStatJpaRepository.save(ProductStatEntity(productId = product.id, likeCount = 0L))

            testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )
            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createAuthHeaders()),
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
            createMember()
            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/999/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 좋아요할 수 없다")
        @Test
        fun returnsNotFound_whenProductIsDeleted() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, isDeleted = true)

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("인증 정보가 올바르지 않으면 좋아요할 수 없다")
        @Test
        fun returnsUnauthorized_whenCredentialsAreInvalid() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Unit>(createAuthHeaders(password = "Wrong123!")),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    inner class UnlikeProduct {
        @DisplayName("상품을 좋아요하지 않은 상태로 만든다")
        @Test
        fun unlikesProduct() {
            val member = createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            productLikeJpaRepository.save(ProductLikeEntity(memberId = member.id, productId = product.id))
            productStatJpaRepository.save(ProductStatEntity(productId = product.id, likeCount = 1L))

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAuthHeaders()),
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
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            productStatJpaRepository.save(ProductStatEntity(productId = product.id, likeCount = 1L))

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAuthHeaders()),
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
            createMember()
            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/999/likes",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 좋아요 취소할 수 없다")
        @Test
        fun returnsNotFound_whenProductIsDeleted() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, isDeleted = true)

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("인증 정보가 올바르지 않으면 좋아요 취소할 수 없다")
        @Test
        fun returnsUnauthorized_whenCredentialsAreInvalid() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)

            val response = testRestTemplate.exchange(
                "$PRODUCTS_ENDPOINT/${product.id}/likes",
                HttpMethod.DELETE,
                HttpEntity<Unit>(createAuthHeaders(password = "Wrong123!")),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    inner class GetLikedProducts {
        @DisplayName("로그인한 회원이 좋아요한 상품 목록을 조회한다")
        @Test
        fun getsLikedProducts() {
            val member = createMember()
            val brand = createBrand()
            val firstProduct = createProduct(brandId = brand.id, name = "loopers hoodie")
            val secondProduct = createProduct(brandId = brand.id, name = "loopers cap")
            productStatJpaRepository.save(ProductStatEntity(productId = firstProduct.id, likeCount = 3L))
            productStatJpaRepository.save(ProductStatEntity(productId = secondProduct.id, likeCount = 5L))
            productLikeJpaRepository.save(ProductLikeEntity(memberId = member.id, productId = firstProduct.id))
            productLikeJpaRepository.save(ProductLikeEntity(memberId = member.id, productId = secondProduct.id))

            val response = testRestTemplate.exchange(
                "$USERS_ENDPOINT/${member.id}/likes",
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data).hasSize(2) },
                { assertThat(response.body?.data?.map { it.productId }).containsExactly(secondProduct.id, firstProduct.id) },
            )
        }

        @DisplayName("삭제된 상품은 좋아요 목록에서 제외한다")
        @Test
        fun excludesDeletedProduct() {
            val member = createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, isDeleted = true)
            productStatJpaRepository.save(ProductStatEntity(productId = product.id, likeCount = 1L))
            productLikeJpaRepository.save(ProductLikeEntity(memberId = member.id, productId = product.id))

            val response = testRestTemplate.exchange(
                "$USERS_ENDPOINT/${member.id}/likes",
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data).isEmpty() },
            )
        }

        @DisplayName("로그인한 회원과 조회 대상이 다르면 조회할 수 없다")
        @Test
        fun returnsUnauthorized_whenMemberIdDoesNotMatchUserId() {
            createMember()
            val response = testRestTemplate.exchange(
                "$USERS_ENDPOINT/2/likes",
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("인증 정보가 올바르지 않으면 좋아요 목록을 조회할 수 없다")
        @Test
        fun returnsUnauthorized_whenCredentialsAreInvalid() {
            val member = createMember()

            val response = testRestTemplate.exchange(
                "$USERS_ENDPOINT/${member.id}/likes",
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders(password = "Wrong123!")),
                object : ParameterizedTypeReference<ApiResponse<List<LikeV1Dto.LikedProductResponse>>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    private fun createBrand(isDeleted: Boolean = false): BrandEntity {
        return brandJpaRepository.save(
            BrandEntity(
                name = "loopers",
                description = "loopers brand",
                logoImageUrl = "https://image.loopers/brand.png",
                isDeleted = isDeleted,
            ),
        )
    }

    private fun createProduct(
        brandId: Long,
        name: String = "loopers hoodie",
        isDeleted: Boolean = false,
    ): ProductEntity {
        return productJpaRepository.save(
            ProductEntity(
                brandId = brandId,
                name = name,
                price = 10_000L,
                description = "loopers product",
                imageUrl = "https://image.loopers/product.png",
                isDeleted = isDeleted,
            ),
        )
    }

    private fun createMember(
        loginId: String = LOGIN_ID,
        password: String = RAW_PASSWORD,
    ): MemberEntity {
        return memberJpaRepository.save(
            MemberEntity(
                loginId = loginId,
                password = PasswordEncoder.encode(password),
                name = "홍길동",
                birthDate = java.time.LocalDate.of(1990, 1, 1),
                email = "$loginId@example.com",
            ),
        )
    }

    private fun createAuthHeaders(
        loginId: String = LOGIN_ID,
        password: String = RAW_PASSWORD,
    ): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-LoginId", loginId)
            set("X-Loopers-LoginPw", password)
        }
    }

    private companion object {
        private const val PRODUCTS_ENDPOINT = "/api/v1/products"
        private const val USERS_ENDPOINT = "/api/v1/users"
        private const val LOGIN_ID = "loopers123"
        private const val RAW_PASSWORD = "Loopers123!"
    }
}
