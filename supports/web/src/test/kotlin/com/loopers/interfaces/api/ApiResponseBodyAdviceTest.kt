package com.loopers.interfaces.api

import com.loopers.support.fixture.WebSupportTestFixtures.apiResponseBodyAdvice
import com.loopers.support.fixture.WebSupportTestFixtures.methodParameter
import com.loopers.support.fixture.WebSupportTestFixtures.request
import com.loopers.support.fixture.WebSupportTestFixtures.response
import com.loopers.support.fixture.WebSupportTestFixtures.successResponse
import com.loopers.support.fixture.WebSupportTestFixtures.testBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.http.MediaType
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

class ApiResponseBodyAdviceTest {
    private val advice = apiResponseBodyAdvice()

    @DisplayName("일반 객체 응답은 ApiResponse 성공 응답으로 감싼다.")
    @Test
    fun wrapsObjectBodyWithSuccessResponse() {
        // arrange
        val body = testBody()

        // act
        val result = advice.beforeBodyWrite(
            body,
            methodParameter(),
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter::class.java,
            request("/api/test"),
            response(),
        )

        // assert
        assertAll(
            { assertThat(result).isInstanceOf(ApiResponse::class.java) },
            { assertThat((result as ApiResponse<*>).isSuccess).isTrue() },
            { assertThat((result as ApiResponse<*>).data).isEqualTo(body) },
        )
    }

    @DisplayName("이미 ApiResponse인 응답은 다시 감싸지 않는다.")
    @Test
    fun doesNotWrapApiResponseAgain() {
        // arrange
        val body = successResponse()

        // act
        val result = advice.beforeBodyWrite(
            body,
            methodParameter(),
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter::class.java,
            request("/api/test"),
            response(),
        )

        // assert
        assertThat(result).isSameAs(body)
    }

    @DisplayName("String 응답은 JSON 문자열로 변환해 반환한다.")
    @Test
    fun writesJsonString_whenStringConverterIsSelected() {
        // act
        val result = advice.beforeBodyWrite(
            "ok",
            methodParameter(),
            MediaType.TEXT_PLAIN,
            StringHttpMessageConverter::class.java,
            request("/api/test"),
            response(),
        )

        // assert
        assertAll(
            { assertThat(result).isInstanceOf(String::class.java) },
            { assertThat(result as String).contains("\"isSuccess\":true") },
            { assertThat(result as String).contains("\"data\":\"ok\"") },
        )
    }

    @DisplayName("actuator 응답은 wrapping 하지 않는다.")
    @Test
    fun doesNotWrapActuatorResponse() {
        // arrange
        val body = mapOf("status" to "UP")

        // act
        val result = advice.beforeBodyWrite(
            body,
            methodParameter(),
            MediaType.APPLICATION_JSON,
            MappingJackson2HttpMessageConverter::class.java,
            request("/actuator/health"),
            response(),
        )

        // assert
        assertThat(result).isSameAs(body)
    }
}
