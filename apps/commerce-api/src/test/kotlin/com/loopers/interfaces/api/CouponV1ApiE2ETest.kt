package com.loopers.interfaces.api

import com.loopers.application.coupon.CreateCouponCommand
import com.loopers.application.user.SignupCommand
import com.loopers.domain.coupon.CouponIssueRequestRepositoryPort
import com.loopers.domain.coupon.CouponType
import com.loopers.interfaces.api.coupon.CouponAdminApplicationServicePort
import com.loopers.interfaces.api.user.UserApplicationServicePort
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userApplicationService: UserApplicationServicePort,
    private val couponAdminApplicationService: CouponAdminApplicationServicePort,
    private val couponIssueRequestRepositoryPort: CouponIssueRequestRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun issueEndpoint(couponId: Long) = "/api/v1/coupons/$couponId/issue"
    private fun issueStatusEndpoint(couponId: Long) = "/api/v1/coupons/$couponId/issue/status"

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

    private fun createTemplate(
        expiredAt: LocalDateTime = LocalDateTime.now().plusDays(30),
        totalCount: Long = 100L,
    ): Long = couponAdminApplicationService.createCoupon(
        CreateCouponCommand(
            name = "1만원 할인",
            type = CouponType.FIXED,
            value = 10_000L,
            minOrderAmount = 0L,
            expiredAt = expiredAt,
            totalCount = totalCount,
        ),
    ).id

    private fun authHeaders(loginId: String?, loginPw: String?): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        loginId?.let { set("X-Loopers-LoginId", it) }
        loginPw?.let { set("X-Loopers-LoginPw", it) }
    }

    private fun issue(
        couponId: Long,
        loginId: String?,
        loginPw: String?,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        return testRestTemplate.exchange(
            issueEndpoint(couponId),
            HttpMethod.POST,
            HttpEntity<Any>(authHeaders(loginId, loginPw)),
            responseType,
        )
    }

    private fun getStatus(
        couponId: Long,
        loginId: String?,
        loginPw: String?,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        return testRestTemplate.exchange(
            issueStatusEndpoint(couponId),
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(loginId, loginPw)),
            responseType,
        )
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    inner class IssueCoupon {
        @DisplayName("정상 발급 요청하면, 200 응답과 PENDING 상태의 발급 요청 ID를 반환하고 DB에 요청이 기록된다.")
        @Test
        fun returnsPending_whenValid() {
            val userId = signup()
            val couponId = createTemplate()

            val response = issue(couponId, "tester01", "password1234")

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat((response.body?.data?.get("couponId") as? Number)?.toLong()).isEqualTo(couponId) },
                { assertThat(response.body?.data?.get("status")).isEqualTo("PENDING") },
                { assertThat(couponIssueRequestRepositoryPort.existsByUserIdAndCouponTemplateId(userId, couponId)).isTrue() },
            )
        }

        @DisplayName("존재하지 않는 쿠폰 템플릿을 발급하면, 404 응답을 받는다.")
        @Test
        fun returnsNotFound_whenTemplateMissing() {
            signup()

            val response = issue(9999L, "tester01", "password1234")

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("만료된 쿠폰 템플릿을 발급하면, 400 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenTemplateExpired() {
            signup()
            val couponId = createTemplate(expiredAt = LocalDateTime.now().minusDays(1))

            val response = issue(couponId, "tester01", "password1234")

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("수량이 0인 쿠폰을 발급 요청하면, 400 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenStockExhausted() {
            signup()
            val couponId = createTemplate(totalCount = 1L)
            signup(loginId = "tester02")
            issue(couponId, "tester02", "password1234")

            val response = issue(couponId, "tester01", "password1234")

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("동일 쿠폰을 두 번 발급 요청하면(1인 1매), 두 번째 요청은 409 응답을 받는다.")
        @Test
        fun returnsConflict_whenAlreadyRequested() {
            val userId = signup()
            val couponId = createTemplate()

            issue(couponId, "tester01", "password1234")
            val response = issue(couponId, "tester01", "password1234")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(couponIssueRequestRepositoryPort.existsByUserIdAndCouponTemplateId(userId, couponId)).isTrue() },
            )
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenLoginFails() {
            signup()
            val couponId = createTemplate()

            val response = issue(couponId, "tester01", "wrong-password!")

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api/v1/coupons/{couponId}/issue/status")
    @Nested
    inner class GetIssueStatus {
        @DisplayName("발급 요청 후 상태를 조회하면, PENDING 상태를 반환한다.")
        @Test
        fun returnsPending_whenRequested() {
            signup()
            val couponId = createTemplate()
            issue(couponId, "tester01", "password1234")

            val response = getStatus(couponId, "tester01", "password1234")

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.data?.get("status")).isEqualTo("PENDING") },
                { assertThat(response.body?.data?.get("failureReason")).isNull() },
            )
        }

        @DisplayName("발급 요청이 없는 상태에서 상태 조회하면, 404 응답을 받는다.")
        @Test
        fun returnsNotFound_whenNoRequest() {
            signup()
            val couponId = createTemplate()

            val response = getStatus(couponId, "tester01", "password1234")

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("잘못된 비밀번호로 상태 조회하면, 401 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenLoginFails() {
            signup()
            val couponId = createTemplate()

            val response = getStatus(couponId, "tester01", "wrong-password!")

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }
}
