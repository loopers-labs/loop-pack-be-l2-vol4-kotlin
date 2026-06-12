package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.example.ExampleModel
import com.loopers.infrastructure.example.ExampleJpaRepository
import com.loopers.interfaces.api.example.ExampleV1Dto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class ExampleV1ApiE2ETest
    @Autowired
    constructor(
        private val exampleJpaRepository: ExampleJpaRepository,
    ) : ApiTest() {
        companion object {
            private val ENDPOINT_GET: (Long) -> String = { id: Long -> "/api/v1/examples/$id" }
        }

        @Test
        fun `존재하는_예시_ID면_예시_정보를_반환한다`() {
            val exampleModel = exampleJpaRepository.save(ExampleModel(name = "예시 제목", description = "예시 설명"))
            val responseType = object : ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>>() {}

            val response = testRestTemplate.exchange(
                ENDPOINT_GET(exampleModel.id),
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            assertThat(response.statusCode.is2xxSuccessful).isTrue()
            assertThat(response.body?.data?.id).isEqualTo(exampleModel.id)
            assertThat(response.body?.data?.name).isEqualTo(exampleModel.name)
            assertThat(response.body?.data?.description).isEqualTo(exampleModel.description)
        }

        @Test
        fun `숫자가_아닌_ID면_400_BAD_REQUEST를_반환한다`() {
            val responseType = object : ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>>() {}

            val response = testRestTemplate.exchange(
                "/api/v1/examples/나나",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            assertThat(response.statusCode.is4xxClientError).isTrue()
            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `존재하지_않는_예시_ID면_404_NOT_FOUND를_반환한다`() {
            val responseType = object : ParameterizedTypeReference<ApiResponse<ExampleV1Dto.ExampleResponse>>() {}

            val response = testRestTemplate.exchange(
                ENDPOINT_GET(-1L),
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                responseType,
            )

            assertThat(response.statusCode.is4xxClientError).isTrue()
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
