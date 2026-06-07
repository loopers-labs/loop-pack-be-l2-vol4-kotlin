package com.loopers.interfaces.api

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
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

    private fun createAndGetId(
        body: Map<String, Any?>,
    ): Long {
        val createResponse = create(body)
        val createdId = ((createResponse.body?.data as? Map<*, *>)?.get("id") as? Number)?.toLong()
        return requireNotNull(createdId) { "쿠폰 생성 후 id를 얻지 못했습니다." }
    }

    @DisplayName("GET /api-admin/v1/coupons/{id}")
    @Nested
    inner class GetCoupon {

        @DisplayName("등록된 템플릿을 단건 조회하면, 해당 템플릿의 전체 필드를 반환한다.")
        @Test
        fun returnsCoupon_whenExists() {
            val createResponse = create(
                mapOf(
                    "name" to "1만원 할인",
                    "type" to "FIXED",
                    "value" to 10_000,
                    "minOrderAmount" to 30_000,
                    "expiredAt" to "2026-12-31T23:59:59",
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
                create(
                    mapOf(
                        "name" to "쿠폰 $index",
                        "type" to "FIXED",
                        "value" to 1_000,
                        "expiredAt" to "2026-12-31T23:59:59",
                    ),
                )
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
            val body = mapOf(
                "name" to "1만원 할인",
                "type" to "FIXED",
                "value" to 10_000,
                "minOrderAmount" to 30_000,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("id")).isNotNull() },
                { assertThat(data?.get("name")).isEqualTo("1만원 할인") },
                { assertThat(data?.get("type")).isEqualTo("FIXED") },
                { assertThat((data?.get("value") as? Number)?.toLong()).isEqualTo(10_000L) },
                { assertThat((data?.get("minOrderAmount") as? Number)?.toLong()).isEqualTo(30_000L) },
            )
        }

        @DisplayName("어드민이 유효한 RATE 쿠폰을 등록하면, type=RATE로 반환한다.")
        @Test
        fun returnsCreatedRateCoupon() {
            val body = mapOf(
                "name" to "10% 할인",
                "type" to "RATE",
                "value" to 10,
                "minOrderAmount" to 10_000,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

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
            val body = mapOf(
                "name" to "조건 없는 할인",
                "type" to "FIXED",
                "value" to 5_000,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat((data?.get("minOrderAmount") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }

        @DisplayName("type 값이 FIXED/RATE가 아니면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInvalidType() {
            val body = mapOf(
                "name" to "잘못된 타입",
                "type" to "PERCENT",
                "value" to 10,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("RATE 할인율이 1~100 범위를 벗어나면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenRateOutOfRange() {
            val body = mapOf(
                "name" to "할인율 초과",
                "type" to "RATE",
                "value" to 150,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("value가 0 이하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenValueNonPositive() {
            val body = mapOf(
                "name" to "0원 할인",
                "type" to "FIXED",
                "value" to 0,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body)

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            val body = mapOf(
                "name" to "권한 없음",
                "type" to "FIXED",
                "value" to 1_000,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body, ldap = null)

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @DisplayName("어드민 헤더 값이 잘못되었으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderInvalid() {
            val body = mapOf(
                "name" to "권한 없음",
                "type" to "FIXED",
                "value" to 1_000,
                "expiredAt" to "2026-12-31T23:59:59",
            )

            val response = create(body, ldap = "wrong.user")

            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{id}")
    @Nested
    inner class UpdateCoupon {

        @DisplayName("어드민이 유효한 값으로 수정하면, 모든 필드가 교체되고 id는 유지된다.")
        @Test
        fun updatesAllFields_andKeepsId() {
            val id = createAndGetId(
                mapOf(
                    "name" to "원본 쿠폰",
                    "type" to "FIXED",
                    "value" to 1_000,
                    "minOrderAmount" to 0,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )

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
            val id = createAndGetId(
                mapOf(
                    "name" to "원본 쿠폰",
                    "type" to "FIXED",
                    "value" to 1_000,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )

            update(
                id,
                mapOf(
                    "name" to "수정됨",
                    "type" to "FIXED",
                    "value" to 2_000,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )

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
                mapOf(
                    "name" to "유령",
                    "type" to "FIXED",
                    "value" to 1_000,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("type 값이 FIXED/RATE가 아니면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInvalidType() {
            val id = createAndGetId(
                mapOf(
                    "name" to "원본",
                    "type" to "FIXED",
                    "value" to 1_000,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )

            val response = update(
                id,
                mapOf(
                    "name" to "원본",
                    "type" to "PERCENT",
                    "value" to 10,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("RATE 할인율이 1~100 범위를 벗어나면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenRateOutOfRange() {
            val id = createAndGetId(
                mapOf(
                    "name" to "원본",
                    "type" to "FIXED",
                    "value" to 1_000,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )

            val response = update(
                id,
                mapOf(
                    "name" to "원본",
                    "type" to "RATE",
                    "value" to 150,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            val response = update(
                1L,
                mapOf(
                    "name" to "권한 없음",
                    "type" to "FIXED",
                    "value" to 1_000,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
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
            val id = createAndGetId(
                mapOf(
                    "name" to "삭제 대상",
                    "type" to "FIXED",
                    "value" to 1_000,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )

            val deleteResponse = delete(id)

            assertAll(
                { assertThat(deleteResponse.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(getCoupon(id).statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
            )
        }

        @DisplayName("삭제된 템플릿은 목록 조회에서도 제외된다.")
        @Test
        fun excludesDeletedFromList() {
            val id = createAndGetId(
                mapOf(
                    "name" to "삭제 대상",
                    "type" to "FIXED",
                    "value" to 1_000,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )

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
            val id = createAndGetId(
                mapOf(
                    "name" to "삭제 대상",
                    "type" to "FIXED",
                    "value" to 1_000,
                    "expiredAt" to "2026-12-31T23:59:59",
                ),
            )
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
}
