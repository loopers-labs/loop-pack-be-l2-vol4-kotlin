package com.loopers.interfaces.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.ResourceHttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody

@RestControllerAdvice
class ApiResponseBodyAdvice(
    private val objectMapper: ObjectMapper,
) : ResponseBodyAdvice<Any> {
    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Boolean {
        val parameterType = returnType.parameterType
        return !ApiResponse::class.java.isAssignableFrom(parameterType) &&
            !StreamingResponseBody::class.java.isAssignableFrom(parameterType)
    }

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        if (shouldSkipWrapping(body, selectedConverterType, request)) {
            return body
        }

        val wrapped = ApiResponse.success(body)
        if (StringHttpMessageConverter::class.java.isAssignableFrom(selectedConverterType)) {
            response.headers.contentType = MediaType.APPLICATION_JSON
            return objectMapper.writeValueAsString(wrapped)
        }

        return wrapped
    }

    private fun shouldSkipWrapping(
        body: Any?,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
    ): Boolean =
        body is ApiResponse<*> ||
            body is ByteArray ||
            ByteArrayHttpMessageConverter::class.java.isAssignableFrom(selectedConverterType) ||
            ResourceHttpMessageConverter::class.java.isAssignableFrom(selectedConverterType) ||
            isExcludedPath(request.uri.path)

    private fun isExcludedPath(path: String): Boolean =
        path.startsWith("/actuator") ||
            path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs")
}
