package com.loopers.interfaces.api

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val ADMIN_BRAND_ENDPOINT = "/api-admin/v1/brands"
        private const val ADMIN_LDAP = "loopers.admin"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun headers(ldap: String? = ADMIN_LDAP, contentType: MediaType? = MediaType.APPLICATION_JSON): HttpHeaders =
        HttpHeaders().apply {
            contentType?.let { setContentType(it) }
            ldap?.let { set("X-Loopers-Ldap", it) }
        }

    private fun adminCreate(
        body: Map<String, String>,
        ldap: String? = ADMIN_LDAP,
    ): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            ADMIN_BRAND_ENDPOINT,
            HttpMethod.POST,
            HttpEntity(body, headers(ldap = ldap)),
            responseType,
        )
    }

    private fun adminGet(
        id: Long,
        ldap: String? = ADMIN_LDAP,
    ): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            "$ADMIN_BRAND_ENDPOINT/$id",
            HttpMethod.GET,
            HttpEntity<Any>(headers(ldap = ldap, contentType = null)),
            responseType,
        )
    }

    private fun adminUpdate(
        id: Long,
        body: Map<String, String>,
        ldap: String? = ADMIN_LDAP,
    ): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            "$ADMIN_BRAND_ENDPOINT/$id",
            HttpMethod.PUT,
            HttpEntity(body, headers(ldap = ldap)),
            responseType,
        )
    }

    private fun adminDelete(
        id: Long,
        ldap: String? = ADMIN_LDAP,
    ): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            "$ADMIN_BRAND_ENDPOINT/$id",
            HttpMethod.DELETE,
            HttpEntity<Any>(headers(ldap = ldap, contentType = null)),
            responseType,
        )
    }

    private fun adminList(
        page: Int? = null,
        size: Int? = null,
        ldap: String? = ADMIN_LDAP,
    ): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val params = buildList {
            page?.let { add("page=$it") }
            size?.let { add("size=$it") }
        }
        val url = if (params.isEmpty()) ADMIN_BRAND_ENDPOINT else "$ADMIN_BRAND_ENDPOINT?${params.joinToString("&")}"
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            url,
            HttpMethod.GET,
            HttpEntity<Any>(headers(ldap = ldap, contentType = null)),
            responseType,
        )
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    inner class CreateBrand {

        @DisplayName("어드민이 유효한 정보를 보내면, id/name/description을 반환한다.")
        @Test
        fun returnsCreatedBrand_whenValidRequest() {
            // arrange
            val body = mapOf("name" to "Nike", "description" to "Just do it")

            // act
            val response = adminCreate(body)

            // assert
            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("name")).isEqualTo("Nike") },
                { assertThat(data?.get("description")).isEqualTo("Just do it") },
                { assertThat(data?.get("id")).isNotNull() },
            )
        }

        @DisplayName("이미 존재하는 name으로 등록하면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun returnsConflict_whenDuplicateName() {
            // arrange
            brandRepositoryPort.save(Brand.create(name = "Nike", description = "기존 설명"))
            val body = mapOf("name" to "Nike", "description" to "Just do it")

            // act
            val response = adminCreate(body)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            // arrange
            val body = mapOf("name" to "Nike", "description" to "Just do it")

            // act
            val response = adminCreate(body, ldap = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @DisplayName("어드민 헤더 값이 잘못되었으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderInvalid() {
            // arrange
            val body = mapOf("name" to "Nike", "description" to "Just do it")

            // act
            val response = adminCreate(body, ldap = "wrong.user")

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{id}")
    @Nested
    inner class GetBrandAdmin {

        @DisplayName("어드민이 존재하는 브랜드 id로 조회하면, id/name/description을 반환한다.")
        @Test
        fun returnsBrandAdmin_whenExists() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "Just do it"))

            // act
            val response = adminGet(saved.id)

            // assert
            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("id")).isEqualTo(saved.id.toInt()) },
                { assertThat(data?.get("name")).isEqualTo("Nike") },
                { assertThat(data?.get("description")).isEqualTo("Just do it") },
            )
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            // act
            val response = adminGet(1L, ldap = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }

        @DisplayName("존재하지 않는 브랜드 id로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandMissing() {
            // act
            val response = adminGet(9999L)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{id}")
    @Nested
    inner class UpdateBrand {

        @DisplayName("어드민이 존재하는 브랜드를 유효한 정보로 수정하면, 변경된 정보를 반환한다.")
        @Test
        fun returnsUpdatedBrand_whenValidRequest() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "old"))
            val body = mapOf("name" to "Nike2", "description" to "new")

            // act
            val response = adminUpdate(saved.id, body)

            // assert
            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("id")).isEqualTo(saved.id.toInt()) },
                { assertThat(data?.get("name")).isEqualTo("Nike2") },
                { assertThat(data?.get("description")).isEqualTo("new") },
            )
        }

        @DisplayName("동일 브랜드의 이름/설명을 그대로 보내면(name이 자기 자신과 같음), 성공한다.")
        @Test
        fun returnsSuccess_whenSameName() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "old"))
            val body = mapOf("name" to "Nike", "description" to "new")

            // act
            val response = adminUpdate(saved.id, body)

            // assert
            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("name")).isEqualTo("Nike") },
                { assertThat(data?.get("description")).isEqualTo("new") },
            )
        }

        @DisplayName("다른 브랜드의 이름과 중복되도록 수정하면, 409 CONFLICT 응답을 받는다.")
        @Test
        fun returnsConflict_whenDuplicateNameWithOtherBrand() {
            // arrange
            brandRepositoryPort.save(Brand.create(name = "Nike", description = "Just do it"))
            val target = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "Impossible is nothing"))
            val body = mapOf("name" to "Nike", "description" to "new")

            // act
            val response = adminUpdate(target.id, body)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        }

        @DisplayName("존재하지 않는 브랜드 id로 수정하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandMissing() {
            // arrange
            val body = mapOf("name" to "Nike2", "description" to "new")

            // act
            val response = adminUpdate(9999L, body)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            // arrange
            val body = mapOf("name" to "Nike2", "description" to "new")

            // act
            val response = adminUpdate(1L, body, ldap = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{id}")
    @Nested
    inner class DeleteBrand {

        @DisplayName("어드민이 존재하는 브랜드를 삭제하면, 성공 응답을 받고 더 이상 조회되지 않는다.")
        @Test
        fun deletesBrand_whenExists() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "Just do it"))

            // act
            val response = adminDelete(saved.id)

            // assert
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
            assertThat(brandRepositoryPort.findById(saved.id)).isNull()
        }

        @DisplayName("존재하지 않는 브랜드 id로 삭제하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandMissing() {
            // act
            val response = adminDelete(9999L)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            // act
            val response = adminDelete(1L, ldap = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    inner class ListBrands {

        @DisplayName("어드민이 브랜드 목록을 조회하면, 페이지 정보와 함께 반환한다.")
        @Test
        fun returnsBrandsWithPaging_whenExists() {
            // arrange
            repeat(3) { idx ->
                brandRepositoryPort.save(Brand.create(name = "brand-$idx", description = "desc-$idx"))
            }

            // act
            val response = adminList(page = 0, size = 10)

            // assert
            val data = response.body?.data as? Map<*, *>
            val items = data?.get("items") as? List<*>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(items?.size).isEqualTo(3) },
                { assertThat(data?.get("page")).isEqualTo(0) },
                { assertThat(data?.get("size")).isEqualTo(10) },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(3L) },
                { assertThat(data?.get("totalPages")).isEqualTo(1) },
            )
        }

        @DisplayName("브랜드가 없으면, 빈 items와 totalElements 0을 반환한다.")
        @Test
        fun returnsEmptyList_whenNoBrands() {
            // act
            val response = adminList(page = 0, size = 10)

            // assert
            val data = response.body?.data as? Map<*, *>
            val items = data?.get("items") as? List<*>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(items?.size).isEqualTo(0) },
                { assertThat((data?.get("totalElements") as? Number)?.toLong()).isEqualTo(0L) },
            )
        }

        @DisplayName("page/size 파라미터가 없으면, 기본값(0, 20)으로 응답한다.")
        @Test
        fun returnsDefaultPaging_whenNoParam() {
            // arrange
            brandRepositoryPort.save(Brand.create(name = "Nike", description = "Just do it"))

            // act
            val response = adminList()

            // assert
            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(data?.get("page")).isEqualTo(0) },
                { assertThat(data?.get("size")).isEqualTo(20) },
            )
        }

        @DisplayName("어드민 헤더가 없으면, 403 FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenLdapHeaderMissing() {
            // act
            val response = adminList(ldap = null)

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }
}
