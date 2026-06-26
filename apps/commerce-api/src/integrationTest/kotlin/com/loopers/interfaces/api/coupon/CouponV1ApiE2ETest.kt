package com.loopers.interfaces.api.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.IssuedCouponStatus
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun issueCouponReturnsIssuedCouponForAuthenticatedUser() {
        saveUser()
        val coupon = saveCoupon()
        val responseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}

        val response = testRestTemplate.exchange(
            "/api/v1/coupons/${coupon.id}/issue",
            HttpMethod.POST,
            HttpEntity<Any>(authHeaders()),
            responseType,
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.couponId).isEqualTo(coupon.id) },
            { assertThat(response.body?.data?.status).isEqualTo(IssuedCouponStatus.AVAILABLE) },
        )
    }

    @Test
    fun getMyCouponsReturnsIssuedCouponStatuses() {
        saveUser()
        val coupon = saveCoupon()
        val issueResponseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}
        testRestTemplate.exchange(
            "/api/v1/coupons/${coupon.id}/issue",
            HttpMethod.POST,
            HttpEntity<Any>(authHeaders()),
            issueResponseType,
        )
        val responseType = object : ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.IssuedCouponResponse>>>() {}

        val response = testRestTemplate.exchange(
            "/api/v1/users/me/coupons",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders()),
            responseType,
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data).hasSize(1) },
            { assertThat(response.body?.data?.single()?.name).isEqualTo("신규가입 10% 할인") },
        )
    }

    @Test
    fun issueCouponRequiresAuthentication() {
        val coupon = saveCoupon()
        val responseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {}

        val response = testRestTemplate.exchange(
            "/api/v1/coupons/${coupon.id}/issue",
            HttpMethod.POST,
            HttpEntity<Any>(HttpHeaders()),
            responseType,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    private fun saveCoupon(): Coupon =
        couponJpaRepository.save(
            Coupon(
                name = "신규가입 10% 할인",
                type = CouponType.RATE,
                value = 10,
                minOrderAmount = 10000,
                expiredAt = LocalDateTime.now().plusDays(30),
            ),
        )

    private fun saveUser(role: UserRole = UserRole.CONSUMER): User =
        userJpaRepository.save(
            User(
                loginId = "loopers01",
                encryptedPassword = passwordEncoder.encode(RawPassword("abcd1234")),
                name = "홍길동",
                birthdate = LocalDate.of(1990, 1, 1),
                email = "user@example.com",
                role = role,
            ),
        )

    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        add("X-Loopers-LoginId", "loopers01")
        add("X-Loopers-LoginPw", "abcd1234")
    }
}
