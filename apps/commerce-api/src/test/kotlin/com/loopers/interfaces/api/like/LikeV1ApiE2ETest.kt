package com.loopers.interfaces.api.like

import com.loopers.domain.catalog.ProductStats
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
import com.loopers.infrastructure.catalog.ProductStatsJpaRepository
import com.loopers.infrastructure.like.ProductLikeHistoryJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
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
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val productLikeHistoryJpaRepository: ProductLikeHistoryJpaRepository,
    private val productStatsJpaRepository: ProductStatsJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val passwordEncoder: PasswordEncoder,
) {
    private val responseType = object : ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikeResponse>>() {}

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    inner class Register {
        @DisplayName("인증된 사용자가 좋아요를 등록하면 liked=true 와 Catalog likeCount=1 을 반환한다.")
        @Test
        fun registersLike_whenAuthenticated() {
            val user = saveUser()
            productStatsJpaRepository.save(ProductStats(productId = 100L))

            val response = testRestTemplate.exchange(
                "/api/v1/products/100/likes",
                HttpMethod.POST,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.productId).isEqualTo(100L) },
                { assertThat(response.body?.data?.liked).isTrue() },
                { assertThat(productLikeHistoryJpaRepository.countByUserIdAndProductId(user.id, 100L)).isEqualTo(1) },
                { assertThat(productStatsJpaRepository.findByProductIdAndDeletedAtIsNull(100L)?.likeCount).isEqualTo(1) },
            )
        }

        @DisplayName("중복 등록은 200 응답이지만 이력과 Catalog likeCount 를 추가로 바꾸지 않는다.")
        @Test
        fun duplicateRegisterIsIdempotent() {
            val user = saveUser()
            productStatsJpaRepository.save(ProductStats(productId = 100L))

            testRestTemplate.exchange("/api/v1/products/100/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), responseType)
            val response = testRestTemplate.exchange("/api/v1/products/100/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), responseType)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.liked).isTrue() },
                { assertThat(productLikeHistoryJpaRepository.countByUserIdAndProductId(user.id, 100L)).isEqualTo(1) },
                { assertThat(productStatsJpaRepository.findByProductIdAndDeletedAtIsNull(100L)?.likeCount).isEqualTo(1) },
            )
        }

        @DisplayName("인증 헤더가 없으면 401 응답을 반환한다.")
        @Test
        fun returnsUnauthorized_whenMissingAuthHeaders() {
            val response = testRestTemplate.exchange(
                "/api/v1/products/100/likes",
                HttpMethod.POST,
                HttpEntity<Any>(HttpHeaders()),
                responseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    inner class Cancel {
        @DisplayName("좋아요 상태에서 취소하면 liked=false 와 Catalog likeCount=0 을 반환한다.")
        @Test
        fun cancelsLike_whenCurrentlyLiked() {
            val user = saveUser()
            productStatsJpaRepository.save(ProductStats(productId = 100L))
            testRestTemplate.exchange("/api/v1/products/100/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), responseType)

            val response = testRestTemplate.exchange(
                "/api/v1/products/100/likes",
                HttpMethod.DELETE,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.liked).isFalse() },
                { assertThat(productLikeHistoryJpaRepository.countByUserIdAndProductId(user.id, 100L)).isEqualTo(2) },
                { assertThat(productStatsJpaRepository.findByProductIdAndDeletedAtIsNull(100L)?.likeCount).isEqualTo(0) },
            )
        }
    }

    @DisplayName("GET /api/v1/products/{productId}/likes/me")
    @Nested
    inner class GetCurrentState {
        @DisplayName("최신 이력이 REGISTER 이면 liked=true 를 반환한다.")
        @Test
        fun returnsCurrentState() {
            saveUser()
            productStatsJpaRepository.save(ProductStats(productId = 100L))
            testRestTemplate.exchange("/api/v1/products/100/likes", HttpMethod.POST, HttpEntity<Any>(authHeaders()), responseType)

            val response = testRestTemplate.exchange(
                "/api/v1/products/100/likes/me",
                HttpMethod.GET,
                HttpEntity<Any>(authHeaders()),
                responseType,
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.productId).isEqualTo(100L) },
                { assertThat(response.body?.data?.liked).isTrue() },
            )
        }
    }

    private fun saveUser(): User =
        userJpaRepository.save(
            User(
                loginId = "loopers01",
                encryptedPassword = passwordEncoder.encode(RawPassword("abcd1234")),
                name = "홍길동",
                birthdate = LocalDate.of(1990, 1, 1),
                email = "user@example.com",
                role = UserRole.CONSUMER,
            ),
        )

    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        add("X-Loopers-LoginId", "loopers01")
        add("X-Loopers-LoginPw", "abcd1234")
    }
}
