package com.loopers.interfaces.api

import com.loopers.application.user.SignupCommand
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepositoryPort
import com.loopers.interfaces.api.user.UserApplicationServicePort
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
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userApplicationService: UserApplicationServicePort,
    private val userCouponRepositoryPort: UserCouponRepositoryPort,
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

    private fun getCoupons(
        page: Int = 0,
        size: Int = 20,
        ldap: String? = ADMIN_LDAP,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        return testRestTemplate.exchange(
            "$ADMIN_COUPON_ENDPOINT?page=$page&size=$size",
            HttpMethod.GET,
            HttpEntity<Void>(headers(ldap = ldap)),
            responseType,
        )
    }

    private fun getCoupon(
        id: Long,
        ldap: String? = ADMIN_LDAP,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        return testRestTemplate.exchange(
            "$ADMIN_COUPON_ENDPOINT/$id",
            HttpMethod.GET,
            HttpEntity<Void>(headers(ldap = ldap)),
            responseType,
        )
    }

    private fun update(
        id: Long,
        body: Map<String, Any?>,
        ldap: String? = ADMIN_LDAP,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        return testRestTemplate.exchange(
            "$ADMIN_COUPON_ENDPOINT/$id",
            HttpMethod.PUT,
            HttpEntity(body, headers(ldap = ldap)),
            responseType,
        )
    }

    private fun delete(
        id: Long,
        ldap: String? = ADMIN_LDAP,
    ): ResponseEntity<ApiResponse<Any>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            "$ADMIN_COUPON_ENDPOINT/$id",
            HttpMethod.DELETE,
            HttpEntity<Void>(headers(ldap = ldap)),
            responseType,
        )
    }

    private fun createAndGetId(body: Map<String, Any?>): Long {
        val createResponse = create(body)
        val createdId = ((createResponse.body?.data as? Map<*, *>)?.get("id") as? Number)?.toLong()
        return requireNotNull(createdId) { "쿠폰 생성 후 id를 얻지 못했습니다." }
    }

    private fun getIssues(
        couponId: Long,
        page: Int = 0,
        size: Int = 20,
        ldap: String? = ADMIN_LDAP,
    ): ResponseEntity<ApiResponse<Map<String, Any?>>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}
        return testRestTemplate.exchange(
            "$ADMIN_COUPON_ENDPOINT/$couponId/issues?page=$page&size=$size",
            HttpMethod.GET,
            HttpEntity<Void>(headers(ldap = ldap)),
            responseType,
        )
    }

    private fun signup(loginId: String): Long =
        userApplicationService.signup(
            SignupCommand(
                loginId = loginId,
                rawPassword = "password1234",
                name = "테스터",
                birth = LocalDate.of(2000, 1, 1),
                email = "$loginId@example.com",
            ),
        ).id

    /** 테스트 데이터 용도로 UserCoupon을 직접 DB에 저장한다. (비동기 발급 플로우 우회) */
    private fun issueTo(loginId: String, couponId: Long) {
        val userId = signup(loginId)
        userCouponRepositoryPort.save(UserCoupon.issue(couponTemplateId = couponId, userId = userId, issuedAt = LocalDateTime.now()))
    }

    /** `totalCount` 필드를 포함한 기본 쿠폰 생성 요청 body. */
    private fun defaultCouponBody(
        name: String = "1만원 할인",
        type: String = "FIXED",
        value: Int = 10_000,
        expiredAt: String = "2026-12-31T23:59:59",
        totalCount: Int = 1000,
        minOrderAmount: Int? = null,
    ): Map<String, Any?> = buildMap {
        put("name", name)
        put("type", type)
        put("value", value)
        put("expiredAt", expiredAt)
        put("totalCount", totalCount)
        if (minOrderAmount != null) put("minOrderAmount", minOrderAmount)
    }

    @DisplayName("GET /api-admin/v1/coupons/{id}")
    @Nested
    inner class GetCoupon {

        @DisplayName("등록된 템플릿을 단건 조회하면, 해당 템플릿의 전체 필드를 반환한다.")
        @Test
        fun returnsCoupon_whenExists() {
            val createResponse = create(
                defaultCouponBody(
                    name = "1만원 할인",
                    value = 10_000,
                    minOrderAmount = 30_000,
                    totalCount = 500,
                ),
            )
            val createdId = ((createResponse.body?.data as? Map<*, *>)?.get("id") as? Number)?.toLong()
            assertThat(createdId).isNotNull()

            val response = getCoupon(requireNotNull(createdId))

            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat((data?.get("id") as? Number)?.toLong()).isEqualTo(createdId) },
                { assertThat(data?.get("name")).isEqualTo("1만원 할인") },
                { assertThat(data?.get("type")).isEqualTo("FIXED") },
                { assertThat((data?.get("value") as? Number)?.toLong()).isEqualTo(10_000L) },
                { assertThat((data?.get("minOrderAmount") as? Number)?.toLong()).isEqualTo(30_000L) },
                { assertThat((data?.get("totalCount") as? Number)?.toLong()).isEqualTo(500L) },
            )
        }

        @DisplayName("존재하지 않는 id로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = getCoupon(9999L)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            val response = getCoupon(1L, ldap = null)

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("GET /api-admin/v1/coupons")
    @Nested
    inner class GetCoupons {

        @DisplayName("등록된 템플릿이 없으면, 빈 목록과 totalElements=0의 페이지를 반환한다.")
        @Test
        fun returnsEmptyPage_whenNoTemplates() {
            val response = getCoupons()

            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("items") as? List<*>).isEmpty() },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }

        @DisplayName("여러 건 등록 후 조회하면, 페이지 크기·총개수가 맞고 최신(id DESC) 순으로 반환한다.")
        @Test
        fun returnsPagedCoupons_sortedByIdDesc() {
            repeat(3) { index ->
                create(defaultCouponBody(name = "쿠폰 $index", value = 1_000))
            }

            val response = getCoupons(page = 0, size = 2)

            val data = response.body?.data
            val items = data?.get("items") as? List<*>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(items).hasSize(2) },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(3L) },
                { assertThat((data?.get("totalPages") as? Number)?.toInt()).isEqualTo(2) },
                { assertThat((items?.first() as? Map<*, *>)?.get("name")).isEqualTo("쿠폰 2") },
            )
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            val response = getCoupons(ldap = null)

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("POST /api-admin/v1/coupons")
    @Nested
    inner class CreateCoupon {

        @DisplayName("어드민이 유효한 FIXED 쿠폰을 등록하면, 생성된 템플릿 전체 필드를 반환한다.")
        @Test
        fun returnsCreatedFixedCoupon() {
            val response = create(defaultCouponBody(name = "1만원 할인", value = 10_000, minOrderAmount = 30_000, totalCount = 100))

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("id")).isNotNull() },
                { assertThat(data?.get("name")).isEqualTo("1만원 할인") },
                { assertThat(data?.get("type")).isEqualTo("FIXED") },
                { assertThat((data?.get("value") as? Number)?.toLong()).isEqualTo(10_000L) },
                { assertThat((data?.get("minOrderAmount") as? Number)?.toLong()).isEqualTo(30_000L) },
                { assertThat((data?.get("totalCount") as? Number)?.toLong()).isEqualTo(100L) },
            )
        }

        @DisplayName("어드민이 유효한 RATE 쿠폰을 등록하면, type=RATE로 반환한다.")
        @Test
        fun returnsCreatedRateCoupon() {
            val response = create(defaultCouponBody(type = "RATE", value = 10, totalCount = 50))

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
            val response = create(defaultCouponBody(name = "조건 없는 할인", value = 5_000))

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat((data?.get("minOrderAmount") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }

        @DisplayName("type 값이 FIXED/RATE가 아니면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInvalidType() {
            val response = create(defaultCouponBody(type = "PERCENT"))

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("RATE 할인율이 1~100 범위를 벗어나면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenRateOutOfRange() {
            val response = create(defaultCouponBody(type = "RATE", value = 150))

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("value가 0 이하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenValueNonPositive() {
            val response = create(defaultCouponBody(value = 0))

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            val response = create(defaultCouponBody(), ldap = null)

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @DisplayName("어드민 헤더 값이 잘못되었으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderInvalid() {
            val response = create(defaultCouponBody(), ldap = "wrong.user")

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{id}")
    @Nested
    inner class UpdateCoupon {

        @DisplayName("어드민이 유효한 값으로 수정하면, 모든 필드가 교체되고 id는 유지된다.")
        @Test
        fun updatesAllFields_andKeepsId() {
            val id = createAndGetId(defaultCouponBody(name = "원본 쿠폰", value = 1_000))

            val response = update(
                id,
                mapOf(
                    "name" to "변경된 쿠폰",
                    "type" to "RATE",
                    "value" to 30,
                    "minOrderAmount" to 50_000,
                    "expiredAt" to "2027-06-30T23:59:59",
                ),
            )

            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat((data?.get("id") as? Number)?.toLong()).isEqualTo(id) },
                { assertThat(data?.get("name")).isEqualTo("변경된 쿠폰") },
                { assertThat(data?.get("type")).isEqualTo("RATE") },
                { assertThat((data?.get("value") as? Number)?.toLong()).isEqualTo(30L) },
                { assertThat((data?.get("minOrderAmount") as? Number)?.toLong()).isEqualTo(50_000L) },
            )
        }

        @DisplayName("수정 결과는 단건 조회에도 반영된다.")
        @Test
        fun reflectsInGet() {
            val id = createAndGetId(defaultCouponBody(name = "원본 쿠폰", value = 1_000))

            update(id, mapOf("name" to "수정됨", "type" to "FIXED", "value" to 2_000, "expiredAt" to "2026-12-31T23:59:59"))

            val data = getCoupon(id).body?.data
            assertAll(
                { assertThat(data?.get("name")).isEqualTo("수정됨") },
                { assertThat((data?.get("value") as? Number)?.toLong()).isEqualTo(2_000L) },
            )
        }

        @DisplayName("존재하지 않는 id를 수정하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = update(
                9999L,
                mapOf("name" to "유령", "type" to "FIXED", "value" to 1_000, "expiredAt" to "2026-12-31T23:59:59"),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("type 값이 FIXED/RATE가 아니면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInvalidType() {
            val id = createAndGetId(defaultCouponBody(name = "원본", value = 1_000))

            val response = update(
                id,
                mapOf("name" to "원본", "type" to "PERCENT", "value" to 10, "expiredAt" to "2026-12-31T23:59:59"),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("RATE 할인율이 1~100 범위를 벗어나면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenRateOutOfRange() {
            val id = createAndGetId(defaultCouponBody(name = "원본", value = 1_000))

            val response = update(
                id,
                mapOf("name" to "원본", "type" to "RATE", "value" to 150, "expiredAt" to "2026-12-31T23:59:59"),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            val response = update(
                1L,
                mapOf("name" to "권한 없음", "type" to "FIXED", "value" to 1_000, "expiredAt" to "2026-12-31T23:59:59"),
                ldap = null,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{id}")
    @Nested
    inner class DeleteCoupon {

        @DisplayName("어드민이 등록된 템플릿을 삭제하면, 2xx 응답을 받고 이후 단건 조회는 404가 된다.")
        @Test
        fun deletesCoupon_andGetReturnsNotFound() {
            val id = createAndGetId(defaultCouponBody(name = "삭제 대상", value = 1_000))

            val deleteResponse = delete(id)

            assertAll(
                { assertThat(deleteResponse.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(getCoupon(id).statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }

        @DisplayName("삭제된 템플릿은 목록 조회에서도 제외된다.")
        @Test
        fun excludesDeletedFromList() {
            val id = createAndGetId(defaultCouponBody(name = "삭제 대상", value = 1_000))

            delete(id)

            val data = getCoupons().body?.data
            assertAll(
                { assertThat(data?.get("items") as? List<*>).isEmpty() },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }

        @DisplayName("존재하지 않는 id를 삭제하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = delete(9999L)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("이미 삭제된 템플릿을 다시 삭제하면, 404 NOT_FOUND 응답을 받는다(멱등 아님).")
        @Test
        fun returnsNotFound_whenAlreadyDeleted() {
            val id = createAndGetId(defaultCouponBody(name = "삭제 대상", value = 1_000))
            delete(id)

            val response = delete(id)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            val response = delete(1L, ldap = null)

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues")
    @Nested
    inner class GetCouponIssues {

        private fun createTemplate(name: String = "1만원 할인"): Long = createAndGetId(
            defaultCouponBody(name = name, value = 10_000, totalCount = 100),
        )

        @DisplayName("발급 내역이 있으면, loginId가 매핑된 발급 항목을 최근 발급순(id DESC)으로 페이지 반환한다.")
        @Test
        fun returnsIssuesWithLoginId_sortedByIdDesc() {
            val couponId = createTemplate()
            issueTo("tester01", couponId)
            issueTo("tester02", couponId)

            val response = getIssues(couponId)

            val data = response.body?.data
            val items = data?.get("items") as? List<*>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(items).hasSize(2) },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(2L) },
                { assertThat((items?.first() as? Map<*, *>)?.get("loginId")).isEqualTo("tester02") },
                { assertThat((items?.get(1) as? Map<*, *>)?.get("loginId")).isEqualTo("tester01") },
                { assertThat((items?.first() as? Map<*, *>)?.get("status")).isEqualTo("AVAILABLE") },
            )
        }

        @DisplayName("size보다 발급이 많으면, 페이지 단위로 잘라 반환하고 totalElements는 전체 개수다.")
        @Test
        fun paginatesIssues() {
            val couponId = createTemplate()
            issueTo("tester01", couponId)
            issueTo("tester02", couponId)
            issueTo("tester03", couponId)

            val response = getIssues(couponId, page = 0, size = 2)

            val data = response.body?.data
            assertAll(
                { assertThat(data?.get("items") as? List<*>).hasSize(2) },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(3L) },
                { assertThat((data?.get("totalPages") as? Number)?.toInt()).isEqualTo(2) },
            )
        }

        @DisplayName("발급 내역이 없는 템플릿이면, 빈 목록과 totalElements=0의 페이지를 반환한다.")
        @Test
        fun returnsEmptyPage_whenNoIssues() {
            val couponId = createTemplate()

            val response = getIssues(couponId)

            val data = response.body?.data
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("items") as? List<*>).isEmpty() },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }

        @DisplayName("존재하지 않는 템플릿의 발급 내역을 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenTemplateMissing() {
            val response = getIssues(9999L)

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            val response = getIssues(1L, ldap = null)

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }
}
