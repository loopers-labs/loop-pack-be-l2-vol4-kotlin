package com.loopers.support.fixture

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.ApiResponseBodyAdvice
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ErrorCode
import com.loopers.support.error.NotFoundException
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.MethodParameter
import org.springframework.http.HttpHeaders
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import java.net.URI

object WebSupportTestFixtures {
    data class TestBody(
        val id: Long = 1L,
        val name: String = "test",
    )

    fun testBody(
        id: Long = 1L,
        name: String = "test",
    ): TestBody =
        TestBody(
            id = id,
            name = name,
        )

    fun successResponse(data: Any? = testBody()): ApiResponse<Any?> =
        ApiResponse.success(data)

    fun badRequestException(
        errorCode: ErrorCode = TestErrorCode.INVALID_VALUE,
        customMessage: String? = null,
    ): BadRequestException =
        BadRequestException(errorCode, customMessage)

    fun notFoundException(
        errorCode: ErrorCode = TestErrorCode.NOT_FOUND,
        customMessage: String? = null,
    ): NotFoundException =
        NotFoundException(errorCode, customMessage)

    fun objectMapper(): ObjectMapper =
        ObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun apiResponseBodyAdvice(): ApiResponseBodyAdvice =
        ApiResponseBodyAdvice(objectMapper())

    fun methodParameter(): MethodParameter =
        mock()

    fun request(path: String): ServerHttpRequest {
        val request = mock<ServerHttpRequest>()
        whenever(request.uri).thenReturn(URI.create("http://localhost$path"))
        return request
    }

    fun response(): ServerHttpResponse {
        val response = mock<ServerHttpResponse>()
        whenever(response.headers).thenReturn(HttpHeaders())
        return response
    }
}

enum class TestErrorCode(
    private val reason: String,
    override val message: String,
) : ErrorCode {
    INVALID_VALUE("INVALID_VALUE", "잘못된 값입니다."),
    NOT_FOUND("NOT_FOUND", "찾을 수 없습니다."),
    ;

    override val code: String
        get() = "TEST:$reason"
}
