package com.loopers.interfaces.api.like

import com.loopers.application.user.UserFacade
import com.loopers.domain.brand.BrandService
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.user.UserService
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
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
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userFacade: UserFacade,
    private val userService: UserService,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val productJpaRepository: ProductJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val loginId = "user123"
    private val rawPassword = "Valid1!pw"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun signUp(): Long {
        userFacade.signUp(loginId, rawPassword, "홍길동", LocalDate.of(1994, 7, 14), "hong@example.com")
        return userService.authenticate(loginId, rawPassword).id
    }

    private fun authHeaders(id: String = loginId, pw: String = rawPassword) = HttpHeaders().apply {
        set("X-Loopers-LoginId", id)
        set("X-Loopers-LoginPw", pw)
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    inner class Like {
        @DisplayName("유효한 인증과 상품 ID로 요청하면, 좋아요가 등록되고 상품 좋아요 수가 1 증가한다.")
        @Test
        fun registersLikeAndIncrementsCount() {
            // arrange
            signUp()
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/products/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            // assert: 집계는 커밋 후 비동기로 반영된다
            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            await().atMost(3, TimeUnit.SECONDS).untilAsserted {
                val reloaded = productJpaRepository.findById(product.id).orElseThrow()
                assertThat(reloaded.likeCount).isEqualTo(1L)
            }
        }

        @DisplayName("이미 좋아요 한 상품에 다시 요청해도, 좋아요 수는 변하지 않는다 (멱등).")
        @Test
        fun isIdempotent_whenAlreadyLiked() {
            // arrange: 첫 좋아요의 집계가 반영될 때까지 대기
            signUp()
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
            testRestTemplate.exchange("/api/v1/products/${product.id}/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), responseType)
            await().atMost(3, TimeUnit.SECONDS).untilAsserted {
                assertThat(productJpaRepository.findById(product.id).orElseThrow().likeCount).isEqualTo(1L)
            }

            // act
            testRestTemplate.exchange("/api/v1/products/${product.id}/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), responseType)

            // assert
            val reloaded = productJpaRepository.findById(product.id).orElseThrow()
            assertThat(reloaded.likeCount).isEqualTo(1L)
        }

        @DisplayName("인증 실패 시, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenAuthFails() {
            // arrange
            signUp()
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
            val response = testRestTemplate.exchange(
                "/api/v1/products/${product.id}/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders(pw = "WrongPw1!")),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("존재하지 않는 상품에 요청하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            // arrange
            signUp()

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
            val response = testRestTemplate.exchange("/api/v1/products/999/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), responseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    inner class Unlike {
        @DisplayName("좋아요를 취소하면, 좋아요 수가 1 감소한다.")
        @Test
        fun decrementsCount() {
            // arrange
            signUp()
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
            testRestTemplate.exchange("/api/v1/products/${product.id}/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), responseType)
            // 좋아요 집계(+1)가 반영된 후 취소해야 감소(-1)와의 실행 순서가 보장된다
            await().atMost(3, TimeUnit.SECONDS).untilAsserted {
                assertThat(productJpaRepository.findById(product.id).orElseThrow().likeCount).isEqualTo(1L)
            }

            // act
            val response = testRestTemplate.exchange("/api/v1/products/${product.id}/likes", HttpMethod.DELETE, HttpEntity<Any>(authHeaders()), responseType)

            // assert: 집계는 커밋 후 비동기로 반영된다
            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            await().atMost(3, TimeUnit.SECONDS).untilAsserted {
                val reloaded = productJpaRepository.findById(product.id).orElseThrow()
                assertThat(reloaded.likeCount).isEqualTo(0L)
            }
        }

        @DisplayName("좋아요한 적 없는 상품을 취소해도, 200 응답을 받는다 (멱등).")
        @Test
        fun isIdempotent_whenNeverLiked() {
            // arrange
            signUp()
            val brand = brandService.register("Nike")
            val product = productService.register(brand.id, "Air Max", 100_000, 10, ProductStatus.ON_SALE)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Unit>>() {}
            val response = testRestTemplate.exchange("/api/v1/products/${product.id}/likes", HttpMethod.DELETE, HttpEntity<Any>(authHeaders()), responseType)

            // assert
            val reloaded = productJpaRepository.findById(product.id).orElseThrow()
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(reloaded.likeCount).isEqualTo(0L) },
            )
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    inner class GetMyLikedProducts {
        @DisplayName("본인 ID로 요청하면, 본인이 좋아요한 상품 목록을 반환한다.")
        @Test
        fun returnsMyLikedProducts() {
            // arrange
            val userId = signUp()
            val brand = brandService.register("Nike")
            val p1 = productService.register(brand.id, "P1", 10_000, 10, ProductStatus.ON_SALE)
            val p2 = productService.register(brand.id, "P2", 20_000, 10, ProductStatus.ON_SALE)
            val responseType = object : ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikedProductPageResponse>>() {}
            testRestTemplate.exchange("/api/v1/products/${p1.id}/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), object : ParameterizedTypeReference<ApiResponse<Unit>>() {})
            testRestTemplate.exchange("/api/v1/products/${p2.id}/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), object : ParameterizedTypeReference<ApiResponse<Unit>>() {})

            // act
            val response = testRestTemplate.exchange("/api/v1/users/$userId/likes", HttpMethod.GET, HttpEntity<Any>(authHeaders()), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2) },
                { assertThat(response.body?.data?.content?.map { it.id }).containsExactly(p2.id, p1.id) },
            )
        }

        @DisplayName("타인의 userId로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenRequestingOthers() {
            // arrange
            val userId = signUp()
            val othersUserId = userId + 1

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikedProductPageResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/users/$othersUserId/likes", HttpMethod.GET, HttpEntity<Any>(authHeaders()), responseType)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("좋아요한 상품이 soft delete 되면, 응답에서 제외된다.")
        @Test
        fun excludesSoftDeletedProducts() {
            // arrange
            val userId = signUp()
            val brand = brandService.register("Nike")
            val live = productService.register(brand.id, "Live", 10_000, 10, ProductStatus.ON_SALE)
            val gone = productService.register(brand.id, "Gone", 20_000, 10, ProductStatus.ON_SALE)
            testRestTemplate.exchange("/api/v1/products/${live.id}/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), object : ParameterizedTypeReference<ApiResponse<Unit>>() {})
            testRestTemplate.exchange("/api/v1/products/${gone.id}/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), object : ParameterizedTypeReference<ApiResponse<Unit>>() {})
            productService.delete(gone.id)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikedProductPageResponse>>() {}
            val response = testRestTemplate.exchange("/api/v1/users/$userId/likes", HttpMethod.GET, HttpEntity<Any>(authHeaders()), responseType)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.content?.map { it.id }).containsExactly(live.id) },
            )
        }
    }
}
