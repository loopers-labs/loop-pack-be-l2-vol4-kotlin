package com.loopers.interfaces.api.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.Page
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ControllerTest @Autowired constructor (
    private val testRestTemplate: TestRestTemplate,
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val requestBaseUrl = "/api-admin/v1/coupons"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    internal inner class GetAll {
        @BeforeEach
        fun init() {
            couponJpaRepository.save(
                CouponModel.of(
                    name = "테스트 쿠폰",
                    type = CouponType.RATE,
                    value = 90.0,
                    minOrderAmount = 12000.0,
                    expiredAt = ZonedDateTime.of(
                        LocalDateTime.of(2020, 1, 1, 0, 0, 0),
                        ZoneId.of("UTC")
                    ),
                    quantity = 100,
                )
            )
            couponJpaRepository.save(
                CouponModel.of(
                    name = "테스트 쿠폰",
                    type = CouponType.RATE,
                    value = 90.0,
                    minOrderAmount = 12000.0,
                    expiredAt = ZonedDateTime.of(
                        LocalDateTime.of(2020, 1, 1, 0, 0, 0),
                        ZoneId.of("UTC")
                    ),
                    quantity = 100,
                )
            )
            couponJpaRepository.save(
                CouponModel.of(
                    name = "테스트 쿠폰",
                    type = CouponType.RATE,
                    value = 90.0,
                    minOrderAmount = 12000.0,
                    expiredAt = ZonedDateTime.of(
                        LocalDateTime.of(2020, 1, 1, 0, 0, 0),
                        ZoneId.of("UTC")
                    ),
                    quantity = 100,
                )
            )
        }
        
        @DisplayName("쿠폰 템플릿 목록을 페이지로 반환한다.")
        @Test
        fun returnCouponPage_whenCouponsExist() {
            // given
            val requestUrl = requestBaseUrl

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>>() {}
            val response: ResponseEntity<ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>>> =
                testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(null), responseType)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body).isNotNull },
                { assertThat(response.body!!.data).isNotNull },
                { assertThat(response.body!!.data!!.content).hasSize(3) },
                { assertThat(response.body!!.data!!.totalElements).isEqualTo(3) },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}")
    @Nested
    internal inner class Get {
        lateinit var couponModel: CouponModel

        @BeforeEach
        fun init() {
            couponModel = couponJpaRepository.save(
                CouponModel.of(
                    name = "테스트 쿠폰",
                    type = CouponType.RATE,
                    value = 90.0,
                    minOrderAmount = 12000.0,
                    expiredAt = ZonedDateTime.of(
                        LocalDateTime.of(2020, 1, 1, 0, 0, 0),
                        ZoneId.of("UTC")
                    ),
                    quantity = 100,
                )
            )
        }
        
        @DisplayName("존재하는 쿠폰 ID를 주면, 해당 쿠폰 정보를 반환한다.")
        @Test
        fun returnCoupon_whenValidCouponIdProvided() {
            // given
            val requestUrl = "$requestBaseUrl/${couponModel.id}"

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<CouponAdminV1Dto.CouponResponse>>() {}
            val response: ResponseEntity<ApiResponse<CouponAdminV1Dto.CouponResponse>> =
                testRestTemplate.exchange(requestUrl, HttpMethod.GET, HttpEntity<Any>(null), responseType)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body).isNotNull },
                { assertThat(response.body!!.data).isNotNull },
            )
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId}")
    @Nested
    internal inner class Delete {
        lateinit var couponModel: CouponModel

        @BeforeEach
        fun init() {
            couponModel = couponJpaRepository.save(
                CouponModel.of("쿠폰", CouponType.RATE, 10.0, 10000.0, ZonedDateTime.now().plusDays(10), 100)
            )
        }

        @DisplayName("템플릿 삭제 시 soft delete 후, 발급된 user_coupon은 보존된다.")
        @Test
        fun softDeleteUserCouponTest() {
            // given
            val request = "$requestBaseUrl/${couponModel.id}"
            val userCoupon = userCouponJpaRepository.save(UserCouponModel.of(couponModel, 1L))

            // when
            val response = testRestTemplate.exchange(request, HttpMethod.DELETE, HttpEntity<Any>(null), object : ParameterizedTypeReference<ApiResponse<Any>>() {})

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(couponJpaRepository.findByIdOrNull(couponModel.id)?.deletedAt).isNotNull() },
                { assertThat(userCouponJpaRepository.findByIdOrNull(userCoupon.id)?.deletedAt).isNull() },
            )
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId}")
    @Nested
    internal inner class Put {
        lateinit var couponModel: CouponModel

        @BeforeEach
        fun init() {
            couponModel = couponJpaRepository.save(
                CouponModel.of("쿠폰", CouponType.RATE, 10.0, 10000.0, ZonedDateTime.now().plusDays(10), 100)
            )
        }

        @DisplayName("이름·만료일은 수정되고, 할인(type/value)은 불변이다")
        @Test
        fun updateNameAndKeepOthersTest() {
            // given
            val request = "$requestBaseUrl/${couponModel.id}"
            val command = CouponAdminV1Dto.UpdateCouponRequest(name = "변경됨", expiredAt = ZonedDateTime.now().plusDays(30))

            // when
            val response = testRestTemplate.exchange(request, HttpMethod.PUT, HttpEntity<Any>(command), object : ParameterizedTypeReference<ApiResponse<Any>>() {})

            // then
            val saved = couponJpaRepository.findByIdOrNull(couponModel.id)!!
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(saved.name).isEqualTo("변경됨") },
                { assertThat(saved.type).isEqualTo(CouponType.RATE) },
                { assertThat(saved.value).isEqualTo(10.0) },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    internal inner class GetHistory {
        lateinit var couponModel: CouponModel

        @BeforeEach
        fun init() {
            couponModel = couponJpaRepository.save(
                CouponModel.of("쿠폰", CouponType.RATE, 10.0, 10000.0, ZonedDateTime.now().plusDays(10), 100),
            )
        }

        @DisplayName("발급 내역을 페이지로 반환하며, 미사용 건의 usedAt 은 null 이다")
        @Test
        fun returnIssues_withNullableUsedAt() {
            // given
            userCouponJpaRepository.save(UserCouponModel.of(couponModel, userId = 1L))
            userCouponJpaRepository.save(UserCouponModel.of(couponModel, userId = 2L).apply { use() })
            val request = "$requestBaseUrl/${couponModel.id}/issues"

            // when
            val response = testRestTemplate.exchange(
                request, HttpMethod.GET, HttpEntity<Any>(null), object : ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.CouponHistoryResponse>>>() {}
            )
            
            // then
            val content = response.body!!.data!!.content
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body!!.data!!.totalElements).isEqualTo(2) },
                { assertThat(content).hasSize(2) },
                { assertThat(content.first { it.userId == 1L }.usedAt).isNull() },
                { assertThat(content.first { it.userId == 2L }.usedAt).isNotNull() },
            )
        }

        @DisplayName("발급 내역이 없으면 빈 페이지를 반환한다")
        @Test
        fun returnEmpty_whenNoIssues() {
            val request = "$requestBaseUrl/${couponModel.id}/issues"
            val response = testRestTemplate.exchange(
                request, HttpMethod.GET, HttpEntity<Any>(null), object : ParameterizedTypeReference<ApiResponse<PageResponse<CouponAdminV1Dto.CouponHistoryResponse>>>() {}
            )
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body!!.data!!.content).isEmpty() },
                { assertThat(response.body!!.data!!.totalElements).isEqualTo(0) },
            )
        }
    }
}
