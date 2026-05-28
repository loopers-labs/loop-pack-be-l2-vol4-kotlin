package com.loopers.interfaces.api

import com.loopers.application.product.CreateProductCommand
import com.loopers.application.user.SignupCommand
import com.loopers.domain.brand.Brand
import com.loopers.interfaces.api.product.ProductAdminApplicationServicePort
import com.loopers.interfaces.api.user.UserApplicationServicePort
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.product.LikeCountQueryPort
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
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val productApplicationService: ProductAdminApplicationServicePort,
    private val userApplicationService: UserApplicationServicePort,
    private val likeCountQueryPort: LikeCountQueryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun likesEndpoint(productId: Long) = "/api/v1/products/$productId/likes"
    private fun userLikesEndpoint(userId: Long) = "/api/v1/users/$userId/likes"

    private fun signup(loginId: String = "tester01", pw: String = "password1234"): Long =
        userApplicationService.signup(
            SignupCommand(
                loginId = loginId,
                rawPassword = pw,
                name = "테스터",
                birth = LocalDate.of(2000, 1, 1),
                email = "$loginId@example.com",
            ),
        ).id

    private fun saveProduct(): Long {
        val brand = brandRepositoryPort.save(Brand.create(name = "Nike-${System.nanoTime()}", description = "x"))
        return productApplicationService.createProduct(
            CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand.id, quantity = 10),
        ).id
    }

    private fun authHeaders(loginId: String?, loginPw: String?): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        loginId?.let { set("X-Loopers-LoginId", it) }
        loginPw?.let { set("X-Loopers-LoginPw", it) }
    }

    private fun postLike(
        productId: Long,
        loginId: String?,
        loginPw: String?,
    ): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            likesEndpoint(productId),
            HttpMethod.POST,
            HttpEntity<Any>(authHeaders(loginId, loginPw)),
            responseType,
        )
    }

    private fun deleteLike(
        productId: Long,
        loginId: String?,
        loginPw: String?,
    ): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            likesEndpoint(productId),
            HttpMethod.DELETE,
            HttpEntity<Any>(authHeaders(loginId, loginPw)),
            responseType,
        )
    }

    private fun getLiked(
        userId: Long,
        loginId: String?,
        loginPw: String?,
        page: Int = 0,
        size: Int = 20,
    ): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            "${userLikesEndpoint(userId)}?page=$page&size=$size",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(loginId, loginPw)),
            responseType,
        )
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    inner class LikeRegister {
        @DisplayName("정상 헤더로 좋아요를 등록하면, 200 응답을 받고 LikeCount가 1 증가한다.")
        @Test
        fun returnsSuccess_whenValid() {
            signup()
            val productId = saveProduct()

            val response = postLike(productId, loginId = "tester01", loginPw = "password1234")

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(1L) },
            )
        }

        @DisplayName("이미 좋아요한 상품에 다시 등록해도 200이며 LikeCount는 1로 유지된다 (멱등).")
        @Test
        fun idempotent_whenDuplicate() {
            signup()
            val productId = saveProduct()

            postLike(productId, "tester01", "password1234")
            val response = postLike(productId, "tester01", "password1234")

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(1L) },
            )
        }

        @DisplayName("존재하지 않는 상품에 좋아요를 등록하면, 404 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductMissing() {
            signup()

            val response = postLike(9999L, "tester01", "password1234")

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenLoginFails() {
            signup()
            val productId = saveProduct()

            val response = postLike(productId, "tester01", "wrong-password!")

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    inner class LikeCancel {
        @DisplayName("등록된 좋아요를 취소하면, 200 응답을 받고 LikeCount가 0이 된다.")
        @Test
        fun returnsSuccess_whenCanceling() {
            signup()
            val productId = saveProduct()
            postLike(productId, "tester01", "password1234")

            val response = deleteLike(productId, "tester01", "password1234")

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(0L) },
            )
        }

        @DisplayName("등록되지 않은 좋아요를 취소해도 200이며 LikeCount는 0 이하로 떨어지지 않는다 (멱등).")
        @Test
        fun idempotent_whenNotExists() {
            signup()
            val productId = saveProduct()

            val response = deleteLike(productId, "tester01", "password1234")

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(likeCountQueryPort.countByProductId(productId)).isEqualTo(0L) },
            )
        }

        @DisplayName("존재하지 않는 상품에 좋아요 취소를 요청하면, 404 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductMissing() {
            signup()

            val response = deleteLike(9999L, "tester01", "password1234")

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    inner class GetLiked {
        @DisplayName("본인의 좋아요 목록을 조회하면, items에 좋아요한 상품들이 포함된다.")
        @Test
        fun returnsLikedProducts_whenOwner() {
            val userId = signup()
            val productId = saveProduct()
            postLike(productId, "tester01", "password1234")

            val response = getLiked(userId, "tester01", "password1234")

            val data = response.body?.data as? Map<*, *>
            val items = data?.get("items") as? List<*>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(1L) },
                { assertThat(items).hasSize(1) },
                {
                    val first = items?.firstOrNull() as? Map<*, *>
                    assertThat((first?.get("productId") as? Number)?.toLong()).isEqualTo(productId)
                    assertThat(first?.get("name")).isEqualTo("p")
                    assertThat(first?.get("brandName") as? String).startsWith("Nike-")
                },
            )
        }

        @DisplayName("타인의 좋아요 목록을 조회하면, 403 응답을 받는다.")
        @Test
        fun returnsForbidden_whenAccessingOtherUser() {
            val ownerId = signup(loginId = "owner01")
            signup(loginId = "stranger01")

            val response = getLiked(ownerId, "stranger01", "password1234")

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @DisplayName("존재하지 않는 userId를 조회하면, 404 응답을 받는다.")
        @Test
        fun returnsNotFound_whenUserMissing() {
            signup()

            val response = getLiked(9999L, "tester01", "password1234")

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("좋아요한 상품이 없으면, 빈 items로 200 응답을 받는다.")
        @Test
        fun returnsEmpty_whenNoLikes() {
            val userId = signup()

            val response = getLiked(userId, "tester01", "password1234")

            val data = response.body?.data as? Map<*, *>
            val items = data?.get("items") as? List<*>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(items).isEmpty() },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }
    }
}
