package com.loopers.interfaces.api

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.coupon.CouponV1Dto
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
import java.time.LocalDate
import java.time.ZonedDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userService: UserService,
    private val couponRepository: CouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue — 발급 성공 시 AVAILABLE 쿠폰을 반환한다.")
    @Test
    fun issuesCoupon() {
        // arrange
        signUp()
        val coupon = saveCoupon()

        // act
        val response = issue(coupon.id)

        // assert
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body!!.data!!.status).isEqualTo(UserCouponStatus.AVAILABLE) },
        )
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue — 중복 발급이면 409를 반환한다.")
    @Test
    fun returnsConflict_whenAlreadyIssued() {
        // arrange
        signUp()
        val coupon = saveCoupon()
        issue(coupon.id)

        // act
        val response = issue(coupon.id)

        // assert
        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }

    private fun issue(couponId: Long) = testRestTemplate.exchange(
        "/api/v1/coupons/$couponId/issue",
        HttpMethod.POST,
        HttpEntity(null, authHeaders()),
        object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.MyCouponResponse>>() {},
    )

    private fun authHeaders() = HttpHeaders().apply {
        set("X-Loopers-LoginId", "tester")
        set("X-Loopers-LoginPw", "Password1!")
    }

    private fun signUp() = userService.signUp(
        UserService.SignUpCommand(
            loginId = "tester",
            password = "Password1!",
            name = "테스터",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "tester@loopers.com",
        ),
    )

    private fun saveCoupon() = couponRepository.save(
        CouponModel(
            name = "정액 쿠폰",
            type = CouponType.FIXED,
            discountValue = BigDecimal("1000"),
            minOrderAmount = null,
            expiredAt = ZonedDateTime.now().plusDays(30),
        ),
    )
}
