package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.기본_로그인_ID
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDateTime

class CouponApiE2ETest
    @Autowired
    constructor(
        private val userService: UserService,
    ) : ApiTest() {
        private val mapResponseType =
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        private val listResponseType =
            object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}

        @Test
        fun `관리자는_쿠폰_템플릿을_생성한다`() {
            val response = createTemplate()

            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(response.body?.data?.get("name")).isEqualTo("WELCOME_10")
            assertThat(response.body?.data?.get("type")).isEqualTo("RATE")
            assertThat(response.body?.data?.get("value")).isEqualTo(10)
        }

        @Test
        fun `사용자는_쿠폰을_발급받고_내_쿠폰_목록에서_AVAILABLE로_조회한다`() {
            userService.signUp(사용자_회원가입())
            val templateId = createTemplate().body?.data?.get("id") as Int

            val issueResponse = testRestTemplate.exchange(
                "/api/v1/coupons/$templateId/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(authHeaders()),
                mapResponseType,
            )
            val myCouponsResponse = testRestTemplate.exchange(
                "/api/v1/users/me/coupons",
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders()),
                listResponseType,
            )

            assertThat(issueResponse.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(issueResponse.body?.data?.get("displayStatus")).isEqualTo("AVAILABLE")
            assertThat(myCouponsResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(myCouponsResponse.body?.data).hasSize(1)
            assertThat(myCouponsResponse.body?.data?.first()?.get("displayStatus")).isEqualTo("AVAILABLE")
        }

        @Test
        fun `같은_쿠폰을_중복_발급하면_409_CONFLICT를_반환한다`() {
            userService.signUp(사용자_회원가입())
            val templateId = createTemplate().body?.data?.get("id") as Int
            testRestTemplate.exchange(
                "/api/v1/coupons/$templateId/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(authHeaders()),
                mapResponseType,
            )

            val response = testRestTemplate.exchange(
                "/api/v1/coupons/$templateId/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(authHeaders()),
                mapResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @Test
        fun `관리자는_쿠폰_템플릿별_발급_이력을_조회한다`() {
            userService.signUp(사용자_회원가입())
            val templateId = createTemplate().body?.data?.get("id") as Int
            testRestTemplate.exchange(
                "/api/v1/coupons/$templateId/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(authHeaders()),
                mapResponseType,
            )

            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$templateId/issues",
                HttpMethod.GET,
                HttpEntity<Unit>(adminHeaders()),
                listResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data).hasSize(1)
            assertThat(response.body?.data?.first()?.get("templateId")).isEqualTo(templateId)
        }

        private fun createTemplate() =
            testRestTemplate.exchange(
                "/api-admin/v1/coupons",
                HttpMethod.POST,
                HttpEntity(
                    mapOf(
                        "name" to "WELCOME_10",
                        "type" to "RATE",
                        "value" to 10,
                        "minOrderAmount" to 10_000,
                        "expiredAt" to LocalDateTime.now().plusDays(7).toString(),
                    ),
                    adminHeaders(),
                ),
                mapResponseType,
            )

        private fun authHeaders(): HttpHeaders {
            val headers = HttpHeaders()
            headers.set("X-Loopers-LoginId", 기본_로그인_ID)
            headers.set("X-Loopers-LoginPw", 기본_비밀번호)
            return headers
        }

        private fun adminHeaders(): HttpHeaders {
            val headers = HttpHeaders()
            headers.set("X-Loopers-Ldap", "admin")
            return headers
        }
    }
