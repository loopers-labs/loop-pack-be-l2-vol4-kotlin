package com.loopers.interfaces.api.admin.coupon

import com.loopers.domain.coupon.DiscountType
import com.loopers.infrastructure.coupon.CouponJpaRepository
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
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    inner class CreateCoupon {
        @DisplayName("관리자가 ZonedDateTime 만료일로 쿠폰 템플릿을 등록한다")
        @Test
        fun createsCoupon() {
            val expiredAt = ZonedDateTime.parse("2026-12-31T23:59:59+09:00")

            val response = testRestTemplate.exchange(
                COUPONS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    AdminCouponV1Dto.CreateCouponRequest(
                        name = "신규가입 10% 할인",
                        type = DiscountType.RATE,
                        value = 10L,
                        minOrderAmount = 10_000L,
                        expiredAt = expiredAt,
                    ),
                    createAdminHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
            )

            val coupons = couponJpaRepository.findAll()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("신규가입 10% 할인") },
                { assertThat(response.body?.data?.expiredAt?.toInstant()).isEqualTo(expiredAt.toInstant()) },
                { assertThat(coupons).hasSize(1) },
                { assertThat(coupons.single().expiredAt.toInstant()).isEqualTo(expiredAt.toInstant()) },
            )
        }
    }

    private fun createAdminHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-Ldap", "admin")
        }
    }

    private companion object {
        private const val COUPONS_ENDPOINT = "/api-admin/v1/coupons"
    }
}
