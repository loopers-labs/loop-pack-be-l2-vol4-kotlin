package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.service.CouponIssueRequestWorker
import com.loopers.domain.coupon.application.service.CouponService
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
import java.util.UUID

class CouponApiE2ETest
    @Autowired
    constructor(
        private val userService: UserService,
        private val couponService: CouponService,
        private val couponIssueRequestWorker: CouponIssueRequestWorker,
    ) : ApiTest() {
        private val mapResponseType =
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        private val listResponseType =
            object : ParameterizedTypeReference<ApiResponse<List<Map<String, Any?>>>>() {}

        @Test
        fun `사용자는_쿠폰을_발급받고_내_쿠폰_목록에서_AVAILABLE로_조회한다`() {
            userService.signUp(사용자_회원가입())
            val templateId = createTemplate().body?.data?.get("id") as Int

            val issueResponse = issueCoupon(templateId)
            val requestId = requestId(issueResponse)
            processIssueRequest(requestId)
            val statusResponse = findIssueRequest(requestId)
            val myCouponsResponse = testRestTemplate.exchange(
                "/api/v1/users/me/coupons",
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders()),
                listResponseType,
            )

            assertThat(issueResponse.statusCode).isEqualTo(HttpStatus.ACCEPTED)
            assertThat(issueResponse.body?.data?.get("status")).isEqualTo("PENDING")
            assertThat(statusResponse.body?.data?.get("status")).isEqualTo("ISSUED")
            assertThat(myCouponsResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(myCouponsResponse.body?.data).hasSize(1)
            assertThat(myCouponsResponse.body?.data?.first()?.get("displayStatus")).isEqualTo("AVAILABLE")
        }

        @Test
        fun `다른_사용자의_쿠폰_발급_요청_상태는_조회할_수_없다`() {
            userService.signUp(사용자_회원가입())
            userService.signUp(사용자_회원가입(loginId = "couponother", email = "coupon-other@example.com"))
            val templateId = createTemplate().body?.data?.get("id") as Int
            val issueResponse = issueCoupon(templateId)
            val requestId = requestId(issueResponse)

            val response = findIssueRequest(requestId, loginId = "couponother")

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @Test
        fun `같은_쿠폰을_중복_발급_요청하면_기존_request를_반환한다`() {
            userService.signUp(사용자_회원가입())
            val templateId = createTemplate().body?.data?.get("id") as Int

            val first = issueCoupon(templateId)
            val second = issueCoupon(templateId)

            assertThat(first.statusCode).isEqualTo(HttpStatus.ACCEPTED)
            assertThat(second.statusCode).isEqualTo(HttpStatus.ACCEPTED)
            assertThat(second.body?.data?.get("requestId")).isEqualTo(first.body?.data?.get("requestId"))
        }

        @Test
        fun `만료된_쿠폰_템플릿은_worker에서_SOLD_OUT으로_확정된다`() {
            userService.signUp(사용자_회원가입())
            val template = couponService.createTemplate(
                CouponTemplateCommand(
                    name = "EXPIRED_10",
                    type = "RATE",
                    value = 10,
                    minOrderAmount = 10_000,
                    expiredAt = LocalDateTime.now().minusDays(1),
                ),
            )

            val issueResponse = issueCoupon(template.id.toInt())
            val requestId = requestId(issueResponse)

            processIssueRequest(requestId)
            val statusResponse = findIssueRequest(requestId)

            assertThat(issueResponse.statusCode).isEqualTo(HttpStatus.ACCEPTED)
            assertThat(statusResponse.body?.data?.get("status")).isEqualTo("SOLD_OUT")
        }

        @Test
        fun `관리자가_삭제한_쿠폰_템플릿은_worker에서_SOLD_OUT으로_확정된다`() {
            userService.signUp(사용자_회원가입())
            val templateId = createTemplate().body?.data?.get("id") as Int

            val deleteResponse = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$templateId",
                HttpMethod.DELETE,
                HttpEntity<Unit>(adminHeaders()),
                mapResponseType,
            )
            val issueResponse = issueCoupon(templateId)
            val requestId = requestId(issueResponse)

            processIssueRequest(requestId)
            val statusResponse = findIssueRequest(requestId)

            assertThat(deleteResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(issueResponse.statusCode).isEqualTo(HttpStatus.ACCEPTED)
            assertThat(statusResponse.body?.data?.get("status")).isEqualTo("SOLD_OUT")
        }

        private fun issueCoupon(templateId: Int) =
            testRestTemplate.exchange(
                "/api/v1/coupons/$templateId/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(authHeaders()),
                mapResponseType,
            )

        private fun findIssueRequest(requestId: UUID) =
            testRestTemplate.exchange(
                "/api/v1/coupons/issue-requests/$requestId",
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders()),
                mapResponseType,
            )

        private fun findIssueRequest(requestId: UUID, loginId: String) =
            testRestTemplate.exchange(
                "/api/v1/coupons/issue-requests/$requestId",
                HttpMethod.GET,
                HttpEntity<Unit>(authHeaders(loginId)),
                mapResponseType,
            )

        private fun requestId(response: org.springframework.http.ResponseEntity<ApiResponse<Map<String, Any?>>>): UUID =
            UUID.fromString(response.body?.data?.get("requestId").toString())

        private fun processIssueRequest(requestId: UUID) {
            couponIssueRequestWorker.process(
                eventId = UUID.randomUUID(),
                consumerGroup = "commerce-api-coupon-issue",
                eventType = "COUPON_ISSUE_REQUESTED_V1",
                requestId = requestId,
            )
        }

        private fun createTemplate(
            name: String = "WELCOME_10",
            expiredAt: LocalDateTime = LocalDateTime.now().plusDays(7),
            totalQuantity: Long = Long.MAX_VALUE,
        ) =
            testRestTemplate.exchange(
                "/api-admin/v1/coupons",
                HttpMethod.POST,
                HttpEntity(
                    mapOf(
                        "name" to name,
                        "type" to "RATE",
                        "value" to 10,
                        "minOrderAmount" to 10_000,
                        "expiredAt" to expiredAt.toString(),
                        "totalQuantity" to totalQuantity,
                    ),
                    adminHeaders(),
                ),
                mapResponseType,
            )

        private fun authHeaders(loginId: String = 기본_로그인_ID): HttpHeaders {
            val headers = HttpHeaders()
            headers.set("X-Loopers-LoginId", loginId)
            headers.set("X-Loopers-LoginPw", 기본_비밀번호)
            return headers
        }

        private fun adminHeaders(): HttpHeaders {
            val headers = HttpHeaders()
            headers.set("X-Loopers-Ldap", "admin")
            return headers
        }
    }
