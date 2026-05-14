package com.loopers.interfaces.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.loopers.support.error.CoreException
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime

data class ApiResponse<T>(
    @get:JsonProperty("isSuccess")
    val isSuccess: Boolean,
    val status: Int,
    val code: String?,
    val message: String?,
    val data: T?,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        fun success(): ApiResponse<Any?> =
            success(null)

        fun <T> success(data: T? = null): ApiResponse<T> =
            ApiResponse(
                isSuccess = true,
                status = HttpStatus.OK.value(),
                code = null,
                message = null,
                data = data,
            )

        fun fail(exception: CoreException): ApiResponse<Any?> =
            ApiResponse(
                isSuccess = false,
                status = exception.status.value(),
                code = exception.errorCode.code,
                message = exception.message,
                data = null,
            )
    }
}
