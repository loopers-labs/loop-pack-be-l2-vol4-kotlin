package com.loopers.interfaces.api.coupon

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.fixture.UserModelFixture
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.ApiResponse
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ControllerTest @Autowired constructor (
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val couponJpaRepository: CouponJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
) {
    private val requestBaseUrl = "/api/v1/coupons"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue")
    @Nested
    internal inner class Issue {
        lateinit var couponModel: CouponModel

        @BeforeEach
        fun init() {
            userJpaRepository.save(UserModelFixture.defaults().toModel())
            couponModel = couponJpaRepository.save(
                CouponModel.of("쿠폰", CouponType.RATE, 10.0, 10000.0, ZonedDateTime.now().plusHours(10), 100),
            )
        }

        @DisplayName("존재하는 쿠폰 ID를 주면 발급된 쿠폰(AVAILABLE)을 반환한다.")
        @Test
        fun issue_returnsAvailable() {
            // given
            val requestUrl = "$requestBaseUrl/${couponModel.id}/issue"
            val headers = HttpHeaders().apply { set("X-Loopers-LoginId", "testId") }

            // when
            val responseType = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponResponse>>() {}
            val response: ResponseEntity<ApiResponse<CouponV1Dto.CouponResponse>> =
                testRestTemplate.exchange(requestUrl, HttpMethod.POST, HttpEntity<Any>(headers), responseType)

            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body!!.data).isNotNull },
                { assertThat(response.body!!.data!!.status).isEqualTo(CouponV1Dto.CouponV1Status.AVAILABLE) },
            )
        }
    }

    @DisplayName("GET /api/v1/coupons/me")
    @Nested
    internal inner class Get {
        @BeforeEach
        fun init() {
            val user = userJpaRepository.save(UserModelFixture.defaults().toModel())
            val valid   = couponJpaRepository.save(CouponModel.of("유효", CouponType.RATE, 10.0, 10000.0, ZonedDateTime.now().plusDays(1), 100))
            val expired = couponJpaRepository.save(CouponModel.of("만료", CouponType.RATE, 10.0, 10000.0, ZonedDateTime.now().minusDays(1), 100))
            userCouponJpaRepository.save(UserCouponModel.of(valid, user.id))
            userCouponJpaRepository.save(UserCouponModel.of(valid, user.id).apply { use() })
            userCouponJpaRepository.save(UserCouponModel.of(expired, user.id))
        }

        @DisplayName("내 쿠폰을 상태와 함께 목록으로 반환한다")
        @Test
        fun returnMyCouponsWithStatus() {
            // given
            val headers = HttpHeaders().apply { set("X-Loopers-LoginId", "testId") }
            val requestUrl = "$requestBaseUrl/me"

            // when
            val response = testRestTemplate.exchange(
                requestUrl, HttpMethod.GET, HttpEntity<Any>(headers), object : ParameterizedTypeReference<ApiResponse<List<CouponV1Dto.CouponResponse>>>() {}
            )
            val content = response.body!!.data!!
            
            // then
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(content).hasSize(3) },
                { assertThat(content.map { it.status }).containsExactlyInAnyOrder(
                    CouponV1Dto.CouponV1Status.AVAILABLE,
                    CouponV1Dto.CouponV1Status.USED,
                    CouponV1Dto.CouponV1Status.EXPIRED,
                ) },
            )
        }
    }
}
