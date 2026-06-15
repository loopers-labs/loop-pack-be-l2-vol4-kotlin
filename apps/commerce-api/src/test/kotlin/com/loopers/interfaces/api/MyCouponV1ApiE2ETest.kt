package com.loopers.interfaces.api

import com.loopers.application.coupon.CreateCouponCommand
import com.loopers.application.user.SignupCommand
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepositoryPort
import com.loopers.interfaces.api.coupon.CouponAdminApplicationServicePort
import com.loopers.interfaces.api.user.UserApplicationServicePort
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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MyCouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userApplicationService: UserApplicationServicePort,
    private val couponAdminApplicationService: CouponAdminApplicationServicePort,
    private val userCouponRepositoryPort: UserCouponRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val endpoint = "/api/v1/users/me/coupons"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

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
        name: String,
        expiredAt: LocalDateTime,
    ): Long = couponAdminApplicationService.createCoupon(
        CreateCouponCommand(
            name = name,
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

    private fun getMyCoupons(
        loginId: String?,
        loginPw: String?,
    ): ResponseEntity<ApiResponse<List<Map<String, Any?>>>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}
        return testRestTemplate.exchange(
            endpoint,
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders(loginId, loginPw)),
            responseType,
        )
    }

    @DisplayName("본인 발급 쿠폰을 AVAILABLE/USED/EXPIRED 상태와 함께 반환한다.")
    @Test
    fun returnsMyCouponsWithStatuses() {
        val userId = signup()
        val now = LocalDateTime.now()
        val availableTemplate = createTemplate("사용 가능", expiredAt = now.plusDays(30))
        val usedTemplate = createTemplate("사용 완료", expiredAt = now.plusDays(30))
        val expiredTemplate = createTemplate("만료", expiredAt = now.minusDays(1))

        // AVAILABLE
        userCouponRepositoryPort.save(
            UserCoupon.issue(couponTemplateId = availableTemplate, userId = userId, issuedAt = now),
        )
        // USED
        userCouponRepositoryPort.save(
            UserCoupon.issue(couponTemplateId = usedTemplate, userId = userId, issuedAt = now).use(now),
        )
        // 만료된 템플릿을 참조하는 AVAILABLE 쿠폰 → 조회 시 EXPIRED 로 표시
        userCouponRepositoryPort.save(
            UserCoupon.issue(couponTemplateId = expiredTemplate, userId = userId, issuedAt = now),
        )

        val response = getMyCoupons("tester01", "password1234")

        val statusByCouponId = response.body?.data?.associate {
            (it["couponId"] as? Number)?.toLong() to it["status"]
        } ?: emptyMap()

        assertAll(
            { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
            { assertThat(response.body?.data).hasSize(3) },
            { assertThat(statusByCouponId[availableTemplate]).isEqualTo("AVAILABLE") },
            { assertThat(statusByCouponId[usedTemplate]).isEqualTo("USED") },
            { assertThat(statusByCouponId[expiredTemplate]).isEqualTo("EXPIRED") },
        )
    }

    @DisplayName("발급받은 쿠폰이 없으면 빈 목록을 반환한다.")
    @Test
    fun returnsEmpty_whenNoCoupons() {
        signup()

        val response = getMyCoupons("tester01", "password1234")

        assertAll(
            { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
            { assertThat(response.body?.data).isEmpty() },
        )
    }

    @DisplayName("잘못된 비밀번호로 요청하면, 401 응답을 받는다.")
    @Test
    fun returnsUnauthorized_whenLoginFails() {
        signup()

        val response = getMyCoupons("tester01", "wrong-password!")

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }
}
