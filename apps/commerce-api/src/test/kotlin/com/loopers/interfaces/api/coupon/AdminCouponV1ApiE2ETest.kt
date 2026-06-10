package com.loopers.interfaces.api.coupon

import com.loopers.domain.coupon.DiscountType
import com.loopers.infrastructure.coupon.CouponEntity
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
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
            val request = createCouponRequest(expiredAt = expiredAt)

            val response = testRestTemplate.exchange(
                COUPONS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
            )

            val coupons = couponJpaRepository.findAll()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.couponId).isPositive() },
                { assertThat(response.body?.data?.name).isEqualTo(request.name) },
                { assertThat(response.body?.data?.type).isEqualTo(request.type) },
                { assertThat(response.body?.data?.value).isEqualTo(request.value) },
                { assertThat(response.body?.data?.minOrderAmount).isEqualTo(request.minOrderAmount) },
                { assertThat(response.body?.data?.expiredAt?.toInstant()).isEqualTo(expiredAt.toInstant()) },
                { assertThat(coupons).hasSize(1) },
                { assertThat(coupons.single().type).isEqualTo(request.type) },
                { assertThat(coupons.single().discountValue).isEqualTo(request.value) },
                { assertThat(coupons.single().minOrderAmount).isEqualTo(request.minOrderAmount) },
                { assertThat(coupons.single().expiredAt.toInstant()).isEqualTo(expiredAt.toInstant()) },
            )
        }

        @DisplayName("관리자 식별 헤더가 없으면 쿠폰 등록에 실패한다")
        @Test
        fun returnsBadRequest_whenAdminHeaderIsMissing() {
            val response = testRestTemplate.exchange(
                COUPONS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(createCouponRequest()),
                object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(couponJpaRepository.findAll()).isEmpty() },
            )
        }

        @DisplayName("관리자 식별 헤더 값이 유효하지 않으면 쿠폰 등록에 실패한다")
        @Test
        fun returnsUnauthorized_whenAdminHeaderValueIsInvalid() {
            val response = testRestTemplate.exchange(
                COUPONS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(createCouponRequest(), createAdminHeaders(adminId = "admin")),
                object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(couponJpaRepository.findAll()).isEmpty() },
            )
        }

        @DisplayName("이미 존재하는 쿠폰명으로 쿠폰 등록 요청 시 실패한다")
        @Test
        fun returnsConflict_whenCouponNameAlreadyExists() {
            val request = createCouponRequest()
            testRestTemplate.exchange(
                COUPONS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
            )

            val response = testRestTemplate.exchange(
                COUPONS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(request, createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(couponJpaRepository.findAll()).hasSize(1) },
            )
        }

        @DisplayName("정률 쿠폰 할인율이 유효하지 않으면 쿠폰 등록에 실패한다")
        @Test
        fun returnsBadRequest_whenRateDiscountValueIsOutOfRange() {
            val response = testRestTemplate.exchange(
                COUPONS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(createCouponRequest(value = 101L), createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(couponJpaRepository.findAll()).isEmpty() },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    inner class GetCoupons {
        @DisplayName("등록된 쿠폰 템플릿 목록을 페이지로 조회한다")
        @Test
        fun returnsCouponPage() {
            val coupon = couponJpaRepository.save(createCouponEntity(name = "신규가입 10% 할인"))
            couponJpaRepository.save(createCouponEntity(name = "삭제된 쿠폰", isDeleted = true))

            val response = testRestTemplate.exchange(
                "$COUPONS_ENDPOINT?page=0&size=20",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<PageResponse<AdminCouponV1Dto.CouponResponse>>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.data).hasSize(1) },
                { assertThat(response.body?.data?.data?.get(0)?.couponId).isEqualTo(coupon.id) },
                { assertThat(response.body?.data?.data?.get(0)?.name).isEqualTo(coupon.name) },
                { assertThat(response.body?.data?.data?.get(0)?.type).isEqualTo(coupon.type) },
                { assertThat(response.body?.data?.data?.get(0)?.value).isEqualTo(coupon.discountValue) },
                { assertThat(response.body?.data?.meta?.totalElements).isEqualTo(1L) },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    inner class GetCoupon {
        @DisplayName("등록된 쿠폰 템플릿 상세를 조회한다")
        @Test
        fun returnsCoupon() {
            val coupon = couponJpaRepository.save(createCouponEntity(name = "신규가입 10% 할인"))

            val response = testRestTemplate.exchange(
                "$COUPONS_ENDPOINT/${coupon.id}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.couponId).isEqualTo(coupon.id) },
                { assertThat(response.body?.data?.name).isEqualTo(coupon.name) },
                { assertThat(response.body?.data?.type).isEqualTo(coupon.type) },
                { assertThat(response.body?.data?.value).isEqualTo(coupon.discountValue) },
                { assertThat(response.body?.data?.minOrderAmount).isEqualTo(coupon.minOrderAmount) },
                { assertThat(response.body?.data?.expiredAt?.toInstant()).isEqualTo(coupon.expiredAt.toInstant()) },
            )
        }

        @DisplayName("삭제된 쿠폰 템플릿은 상세 조회할 수 없다")
        @Test
        fun returnsNotFound_whenCouponIsDeleted() {
            val coupon = couponJpaRepository.save(createCouponEntity(name = "삭제된 쿠폰", isDeleted = true))

            val response = testRestTemplate.exchange(
                "$COUPONS_ENDPOINT/${coupon.id}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAdminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<AdminCouponV1Dto.CouponResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    private fun createCouponRequest(
        name: String = "신규가입 10% 할인",
        type: DiscountType = DiscountType.RATE,
        value: Long = 10L,
        minOrderAmount: Long? = 10_000L,
        expiredAt: ZonedDateTime = ZonedDateTime.parse("2026-12-31T23:59:59+09:00"),
    ): AdminCouponV1Dto.CreateCouponRequest {
        return AdminCouponV1Dto.CreateCouponRequest(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
    }

    private fun createCouponEntity(
        name: String = "신규가입 10% 할인",
        type: DiscountType = DiscountType.RATE,
        discountValue: Long = 10L,
        minOrderAmount: Long? = 10_000L,
        expiredAt: ZonedDateTime = ZonedDateTime.parse("2026-12-31T23:59:59+09:00"),
        isDeleted: Boolean = false,
    ): CouponEntity {
        return CouponEntity(
            name = name,
            type = type,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
            isDeleted = isDeleted,
        )
    }

    private fun createAdminHeaders(adminId: String = "loopers.admin"): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-Ldap", adminId)
            contentType = MediaType.APPLICATION_JSON
        }
    }

    private companion object {
        private const val COUPONS_ENDPOINT = "/api-admin/v1/coupons"
    }
}
