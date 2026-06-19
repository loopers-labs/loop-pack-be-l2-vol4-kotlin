package com.loopers.interfaces.api

import com.loopers.infrastructure.brand.BrandJpaEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.interfaces.api.brand.BrandV1Dto
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private val ENDPOINT_GET: (Long) -> String = { id: Long -> "/api/v1/brands/$id" }
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    inner class GetBrand {
        @DisplayName("존재하는 브랜드 ID이면 브랜드 정보를 반환한다.")
        @Test
        fun returnsBrand_whenBrandExists() {
            // arrange
            val entity = brandJpaRepository.save(
                BrandJpaEntity(
                    name = "Loopers",
                    description = "감성 이커머스 브랜드",
                    logoImageUrl = "https://example.com/logo.png",
                ),
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET(entity.id),
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(entity.id) },
                { assertThat(response.body?.data?.name).isEqualTo("Loopers") },
                { assertThat(response.body?.data?.description).isEqualTo("감성 이커머스 브랜드") },
                { assertThat(response.body?.data?.logoImageUrl).isEqualTo("https://example.com/logo.png") },
            )
        }

        @DisplayName("삭제된 브랜드 ID이면 404 NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandIsDeleted() {
            // arrange
            val entity = brandJpaRepository.save(
                BrandJpaEntity(
                    name = "Loopers",
                    description = "감성 이커머스 브랜드",
                    logoImageUrl = null,
                ),
            )
            entity.delete()
            brandJpaRepository.save(entity)

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>>() {}
            val response = testRestTemplate.exchange(
                ENDPOINT_GET(entity.id),
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
}
