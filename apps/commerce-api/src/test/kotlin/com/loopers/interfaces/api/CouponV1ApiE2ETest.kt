package com.loopers.interfaces.api

import com.loopers.application.coupon.CreateCouponCommand
import com.loopers.application.user.SignupCommand
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponRepositoryPort
import com.loopers.interfaces.api.coupon.CouponAdminApplicationServicePort
import com.loopers.interfaces.api.user.UserApplicationServicePort
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
import org.springframework.http.ResponseEntity
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userApplicationService: UserApplicationServicePort,
    private val couponAdminApplicationService: CouponAdminApplicationServicePort,
    private val userCouponRepositoryPort: UserCouponRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun issueEndpoint(couponId: Long) = "/api/v1/coupons/$couponId/issue"

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
    ): Long = couponAdminApplicationService.createCoupon(
        CreateCouponCommand(
            name = "1만원 할인",
            type = CouponType.FIXED,
            value = 10_000L,
            minOrderAmount = 0L,
            expiredAt = expiredAt,
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

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    inner class IssueCoupon {
        @DisplayName("정상 발급하면, 200 응답을 받고 AVAILABLE 발급 쿠폰이 DB에 생성된다.")
        @Test
        fun returnsSuccess_whenValid() {
            val userId = signup()
            val couponId = createTemplate()

            val response = issue(couponId, "tester01", "password1234")

            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat((response.body?.data?.get("couponId") as? Number)?.toLong()).isEqualTo(couponId) },
                { assertThat(response.body?.data?.get("status")).isEqualTo("AVAILABLE") },
                { assertThat(userCouponRepositoryPort.existsByUserIdAndCouponTemplateId(userId, couponId)).isTrue() },
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

        @DisplayName("동일 쿠폰을 두 번 발급하면(1인 1매), 두 번째 요청은 409 응답을 받는다.")
        @Test
        fun returnsConflict_whenAlreadyIssued() {
            val userId = signup()
            val couponId = createTemplate()

            issue(couponId, "tester01", "password1234")
            val response = issue(couponId, "tester01", "password1234")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(userCouponRepositoryPort.existsByUserIdAndCouponTemplateId(userId, couponId)).isTrue() },
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
}
