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
class BrandV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val BRAND_DETAIL_ENDPOINT = "/api/v1/brands"
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun getBrand(id: Long): org.springframework.http.ResponseEntity<ApiResponse<Any>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        return testRestTemplate.exchange(
            "$BRAND_DETAIL_ENDPOINT/$id",
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            responseType,
        )
    }

    @DisplayName("GET /api/v1/brands/{id}")
    @Nested
    inner class GetBrandDetail {

        @DisplayName("존재하는 브랜드 id로 조회하면, id/name/description을 반환한다.")
        @Test
        fun returnsBrand_whenExists() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "Just do it"))

            // act
            val response = getBrand(saved.id)

            // assert
            val data = response.body?.data as? Map<*, *>
            assertAll(
                { assertThat(response.statusCode.is2xxSuccessful).isTrue() },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(data?.get("id")).isEqualTo(saved.id.toInt()) },
                { assertThat(data?.get("name")).isEqualTo("Nike") },
                { assertThat(data?.get("description")).isEqualTo("Just do it") },
            )
        }

        @DisplayName("존재하지 않는 브랜드 id로 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandMissing() {
            // act
            val response = getBrand(9999L)

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }
    }
}
