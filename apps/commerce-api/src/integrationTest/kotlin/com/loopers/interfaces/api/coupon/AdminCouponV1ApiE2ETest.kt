package com.loopers.interfaces.api.coupon

import com.loopers.domain.coupon.CouponType
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
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
class AdminCouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun adminCanCreateListDetailUpdateDeleteCouponTemplateAndReadIssues() {
        saveUser(UserRole.ADMIN)
        val createResponseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponResponse>>() {}
        val createBody = mapOf(
            "name" to "신규가입 10% 할인",
            "type" to "RATE",
            "value" to 10,
            "minOrderAmount" to 10000,
            "expiredAt" to LocalDateTime.now().plusDays(30).toString(),
        )

        val createdResponse = testRestTemplate.exchange(
            "/api-admin/v1/coupons",
            HttpMethod.POST,
            HttpEntity(createBody, authHeaders()),
            createResponseType,
        )
        val couponId = createdResponse.body?.data?.couponId!!

        val updateBody = mapOf(
            "name" to "신규가입 3000원 할인",
            "type" to "FIXED",
            "value" to 3000,
            "minOrderAmount" to null,
            "expiredAt" to LocalDateTime.now().plusDays(60).toString(),
        )
        val updatedResponse = testRestTemplate.exchange(
            "/api-admin/v1/coupons/$couponId",
            HttpMethod.PUT,
            HttpEntity(updateBody, authHeaders()),
            createResponseType,
        )
        val listResponseType = object : ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.CouponResponse>>>() {}
        val listResponse = testRestTemplate.exchange(
            "/api-admin/v1/coupons?page=0&size=20",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders()),
            listResponseType,
        )
        val issuesResponseType = object : ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.IssuedCouponResponse>>>() {}
        val issuesResponse = testRestTemplate.exchange(
            "/api-admin/v1/coupons/$couponId/issues?page=0&size=20",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders()),
            issuesResponseType,
        )
        val deleteResponse = testRestTemplate.exchange(
            "/api-admin/v1/coupons/$couponId",
            HttpMethod.DELETE,
            HttpEntity<Any>(authHeaders()),
            object : ParameterizedTypeReference<ApiResponse<Unit>>() {},
        )

        assertAll(
            { assertThat(createdResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(updatedResponse.body?.data?.name).isEqualTo("신규가입 3000원 할인") },
            { assertThat(updatedResponse.body?.data?.type).isEqualTo(CouponType.FIXED) },
            { assertThat(listResponse.body?.data?.map { it.couponId }).contains(couponId) },
            { assertThat(issuesResponse.body?.data).isEmpty() },
            { assertThat(deleteResponse.statusCode).isEqualTo(HttpStatus.OK) },
        )
    }

    @Test
    fun consumerCannotCallAdminCouponApi() {
        saveUser(UserRole.CONSUMER)
        val responseType = object : ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.CouponResponse>>>() {}

        val response = testRestTemplate.exchange(
            "/api-admin/v1/coupons?page=0&size=20",
            HttpMethod.GET,
            HttpEntity<Any>(authHeaders()),
            responseType,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
    }

    private fun saveUser(role: UserRole): User =
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
