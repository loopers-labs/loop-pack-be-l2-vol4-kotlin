package com.loopers.interfaces.api

import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.interfaces.api.coupon.AdminCouponV1Dto
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
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminCouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("X-Loopers-Ldap 헤더가 없으면 401을 반환한다.")
    @Test
    fun returnsUnauthorized_withoutLdapHeader() {
        // act
        val response = testRestTemplate.exchange(
            "/api-admin/v1/coupons",
            HttpMethod.GET,
            HttpEntity(null, HttpHeaders()),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )

        // assert
        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @DisplayName("쿠폰 템플릿 등록, 상세 조회, 수정, 삭제 흐름이 동작한다.")
    @Test
    fun crudRoundTrip() {
        // arrange & act
        val created = createCoupon()
        val couponId = created.body!!.data!!.id
        val detail = getCoupon(couponId)
        val updated = testRestTemplate.exchange(
            "/api-admin/v1/coupons/$couponId",
            HttpMethod.PUT,
            HttpEntity(createRequest().copy(name = "변경된 쿠폰"), adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
        )
        val deleted = testRestTemplate.exchange(
            "/api-admin/v1/coupons/$couponId",
            HttpMethod.DELETE,
            HttpEntity(null, adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
        val detailAfterDelete = getCoupon(couponId)

        // assert
        assertAll(
            { assertThat(created.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(detail.body!!.data!!.name).isEqualTo("신규가입 10% 할인") },
            { assertThat(updated.body!!.data!!.name).isEqualTo("변경된 쿠폰") },
            { assertThat(deleted.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(detailAfterDelete.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            { assertThat(couponRepository.findActiveById(couponId)).isNull() },
        )
    }

    @DisplayName("쿠폰 템플릿 목록을 페이징으로 조회한다.")
    @Test
    fun listsCouponsWithPaging() {
        // arrange
        repeat(3) { index ->
            createCoupon(createRequest().copy(name = "쿠폰$index"))
        }

        // act
        val response = testRestTemplate.exchange(
            "/api-admin/v1/coupons?page=0&size=2",
            HttpMethod.GET,
            HttpEntity(null, adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponPageResponse>>() {},
        )

        // assert
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body!!.data!!.items).hasSize(2) },
            { assertThat(response.body!!.data!!.totalCount).isEqualTo(3L) },
        )
    }

    @DisplayName("특정 쿠폰의 발급 내역을 조회한다.")
    @Test
    fun listsIssuedCoupons() {
        // arrange
        val couponId = createCoupon().body!!.data!!.id
        userCouponRepository.save(UserCouponModel(userId = 1L, couponId = couponId))
        userCouponRepository.save(UserCouponModel(userId = 2L, couponId = couponId))

        // act
        val response = testRestTemplate.exchange(
            "/api-admin/v1/coupons/$couponId/issues?page=0&size=20",
            HttpMethod.GET,
            HttpEntity(null, adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponIssuePageResponse>>() {},
        )

        // assert
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body!!.data!!.items).hasSize(2) },
            { assertThat(response.body!!.data!!.totalCount).isEqualTo(2L) },
        )
    }

    private fun createCoupon(
        request: AdminCouponV1Dto.CouponUpsertRequest = createRequest(),
    ) = testRestTemplate.exchange(
        "/api-admin/v1/coupons",
        HttpMethod.POST,
        HttpEntity(request, adminHeaders()),
        object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
    )

    private fun getCoupon(couponId: Long) = testRestTemplate.exchange(
        "/api-admin/v1/coupons/$couponId",
        HttpMethod.GET,
        HttpEntity(null, adminHeaders()),
        object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
    )

    private fun adminHeaders() = HttpHeaders().apply { set("X-Loopers-Ldap", "admin.user") }

    private fun createRequest() = AdminCouponV1Dto.CouponUpsertRequest(
        name = "신규가입 10% 할인",
        type = CouponType.RATE,
        value = BigDecimal("10"),
        minOrderAmount = BigDecimal("10000"),
        expiredAt = LocalDateTime.of(2026, 12, 31, 23, 59, 59),
    )
}
