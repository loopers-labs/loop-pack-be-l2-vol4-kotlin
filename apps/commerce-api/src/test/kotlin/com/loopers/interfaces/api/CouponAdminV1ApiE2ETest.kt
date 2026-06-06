package com.loopers.interfaces.api

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ADMIN_COUPON_ENDPOINT = "/api-admin/v1/coupons"
        private const val ADMIN_LDAP = "loopers.admin"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun headers(ldap: String? = ADMIN_LDAP): HttpHeaders =
        HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            ldap?.let { set("X-Loopers-Ldap", it) }
        }

    private fun create(
        body: Map<String, Any?>,
        ldap: String? = ADMIN_LDAP,
    ): ResponseEntity<ApiResponse<Any>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            ADMIN_COUPON_ENDPOINT,
            HttpMethod.POST,
            HttpEntity(body, headers(ldap = ldap)),
            responseType,
        )
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    inner class CreateCoupon {

        @DisplayName("어드민이 유효한 FIXED 쿠폰을 등록하면, 생성된 템플릿 전체 필드를 반환한다.")
        @Test
        fun returnsCreatedFixedCoupon() {
            val body = mapOf(
                "name" to "1만원 할인",
                "type" to "FIXED",
                "value" to 10_000,
                "minOrderAmount" to 30_000,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("id")).isNotNull() },
                { assertThat(data?.get("name")).isEqualTo("1만원 할인") },
                { assertThat(data?.get("type")).isEqualTo("FIXED") },
                { assertThat((data?.get("value") as? Number)?.toLong()).isEqualTo(10_000L) },
                { assertThat((data?.get("minOrderAmount") as? Number)?.toLong()).isEqualTo(30_000L) },
            )
        }

        @DisplayName("어드민이 유효한 RATE 쿠폰을 등록하면, type=RATE로 반환한다.")
        @Test
        fun returnsCreatedRateCoupon() {
            val body = mapOf(
                "name" to "10% 할인",
                "type" to "RATE",
                "value" to 10,
                "minOrderAmount" to 10_000,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("type")).isEqualTo("RATE") },
                { assertThat((data?.get("value") as? Number)?.toLong()).isEqualTo(10L) },
            )
        }

        @DisplayName("minOrderAmount를 생략하면, 0으로 기본 처리되어 등록된다.")
        @Test
        fun defaultsMinOrderAmountToZero_whenOmitted() {
            val body = mapOf(
                "name" to "조건 없는 할인",
                "type" to "FIXED",
                "value" to 5_000,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat((data?.get("minOrderAmount") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }

        @DisplayName("type 값이 FIXED/RATE가 아니면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInvalidType() {
            val body = mapOf(
                "name" to "잘못된 타입",
                "type" to "PERCENT",
                "value" to 10,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("RATE 할인율이 1~100 범위를 벗어나면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenRateOutOfRange() {
            val body = mapOf(
                "name" to "할인율 초과",
                "type" to "RATE",
                "value" to 150,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("value가 0 이하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenValueNonPositive() {
            val body = mapOf(
                "name" to "0원 할인",
                "type" to "FIXED",
                "value" to 0,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            val body = mapOf(
                "name" to "권한 없음",
                "type" to "FIXED",
                "value" to 1_000,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body, ldap = null)

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @DisplayName("어드민 헤더 값이 잘못되었으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderInvalid() {
            val body = mapOf(
                "name" to "권한 없음",
                "type" to "FIXED",
                "value" to 1_000,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body, ldap = "wrong.user")

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }
}
