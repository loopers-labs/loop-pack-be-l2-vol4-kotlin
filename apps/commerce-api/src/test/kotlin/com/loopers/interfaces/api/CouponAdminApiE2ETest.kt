package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.coupon.application.service.CouponIssueRequestWorker
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

class CouponAdminApiE2ETest
    @Autowired
    constructor(
        private val userService: UserService,
        private val couponIssueRequestWorker: CouponIssueRequestWorker,
    ) : ApiTest() {
        private val mapResponseType =
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        private val pageResponseType =
            object : ParameterizedTypeReference<ApiResponse<PageResponse<Map<String, Any?>>>>() {}

        @Test
        fun `관리자는_쿠폰_템플릿을_생성한다`() {
            val response = createTemplate()

            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(response.body?.data?.get("name")).isEqualTo("WELCOME_10")
            assertThat(response.body?.data?.get("type")).isEqualTo("RATE")
            assertThat(response.body?.data?.get("value")).isEqualTo(10)
        }

        @Test
        fun `관리자는_쿠폰_템플릿별_발급_이력을_조회한다`() {
            userService.signUp(사용자_회원가입())
            val templateId = createTemplate().body?.data?.get("id") as Int
            val issueResponse = issueCoupon(templateId)
            processIssueRequest(requestId(issueResponse))

            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$templateId/issues",
                HttpMethod.GET,
                HttpEntity<Unit>(adminHeaders()),
                pageResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.content).hasSize(1)
            assertThat(response.body?.data?.content?.first()?.get("templateId")).isEqualTo(templateId)
        }

        @Test
        fun `관리자는_쿠폰_템플릿을_수정하면_변경_내용이_반영된다`() {
            val templateId = createTemplate().body?.data?.get("id") as Int

            val updateResponse = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$templateId",
                HttpMethod.PUT,
                HttpEntity(
                    mapOf(
                        "name" to "UPDATED_5000",
                        "type" to "FIXED",
                        "value" to 5_000,
                        "minOrderAmount" to 20_000,
                        "expiredAt" to LocalDateTime.now().plusDays(30).toString(),
                    ),
                    adminHeaders(),
                ),
                mapResponseType,
            )
            val findResponse = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$templateId",
                HttpMethod.GET,
                HttpEntity<Unit>(adminHeaders()),
                mapResponseType,
            )

            assertThat(updateResponse.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(findResponse.body?.data?.get("name")).isEqualTo("UPDATED_5000")
            assertThat(findResponse.body?.data?.get("type")).isEqualTo("FIXED")
            assertThat(findResponse.body?.data?.get("value")).isEqualTo(5_000)
            assertThat(findResponse.body?.data?.get("minOrderAmount")).isEqualTo(20_000)
        }

        @Test
        fun `관리자는_쿠폰_템플릿_목록을_페이지로_조회한다`() {
            val totalCount = 5
            val size = 2
            repeat(totalCount) { index ->
                createTemplate(name = "PAGED_$index")
            }

            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons?page=0&size=$size",
                HttpMethod.GET,
                HttpEntity<Unit>(adminHeaders()),
                pageResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.content).hasSize(size)
            assertThat(response.body?.data?.totalElements).isEqualTo(totalCount.toLong())
            assertThat(response.body?.data?.totalPages).isEqualTo(3)
            assertThat(response.body?.data?.hasNext).isEqualTo(true)
            assertThat(response.body?.data?.content?.first()?.get("name")).isEqualTo("PAGED_4")
            assertThat(response.body?.data?.content?.last()?.get("name")).isEqualTo("PAGED_3")
        }

        @Test
        fun `관리자는_쿠폰_템플릿별_발급_이력을_페이지로_조회한다`() {
            val templateId = createTemplate(totalQuantity = 3).body?.data?.get("id") as Int
            val totalCount = 3
            val size = 2
            (1..totalCount).forEach { index ->
                userService.signUp(사용자_회원가입(loginId = "issuer%04d".format(index), email = "issuer$index@example.com"))
                val issueResponse = testRestTemplate.exchange(
                    "/api/v1/coupons/$templateId/issue",
                    HttpMethod.POST,
                    HttpEntity<Unit>(authHeaders(loginId = "issuer%04d".format(index))),
                    mapResponseType,
                )
                processIssueRequest(requestId(issueResponse))
            }

            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$templateId/issues?page=0&size=$size",
                HttpMethod.GET,
                HttpEntity<Unit>(adminHeaders()),
                pageResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.content).hasSize(size)
            assertThat(response.body?.data?.totalElements).isEqualTo(totalCount.toLong())
            assertThat(response.body?.data?.totalPages).isEqualTo(2)
            assertThat(response.body?.data?.hasNext).isEqualTo(true)
            assertThat(response.body?.data?.content?.map { it["templateId"] }).containsOnly(templateId)
        }

        private fun issueCoupon(templateId: Int) =
            testRestTemplate.exchange(
                "/api/v1/coupons/$templateId/issue",
                HttpMethod.POST,
                HttpEntity<Unit>(authHeaders()),
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
