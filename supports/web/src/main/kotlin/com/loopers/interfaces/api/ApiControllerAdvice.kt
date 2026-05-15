package com.loopers.interfaces.api

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.CommonErrorCode
import com.loopers.support.error.ConflictException
import com.loopers.support.error.CoreException
import com.loopers.support.error.ForbiddenException
import com.loopers.support.error.InternalServerException
import com.loopers.support.error.NotFoundException
import com.loopers.support.error.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.servlet.resource.NoResourceFoundException

@RestControllerAdvice
class ApiControllerAdvice {
    private val log = LoggerFactory.getLogger(ApiControllerAdvice::class.java)

    @ExceptionHandler
    fun handle(e: CoreException): ResponseEntity<ApiResponse<*>> {
        log.warn("CoreException : {}", e.message, e)
        return failureResponse(e)
    }

    @ExceptionHandler
    fun handleBadRequest(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<*>> {
        val type = e.requiredType?.simpleName ?: "unknown"
        val value = e.value ?: "null"
        val message = "요청 파라미터 '${e.name}' (타입: $type)의 값 '$value'이(가) 잘못되었습니다."
        return failureResponse(BadRequestException(CommonErrorCode.BAD_REQUEST, message))
    }

    @ExceptionHandler
    fun handleBadRequest(e: MissingServletRequestParameterException): ResponseEntity<ApiResponse<*>> {
        val message = "필수 요청 파라미터 '${e.parameterName}' (타입: ${e.parameterType})가 누락되었습니다."
        return failureResponse(BadRequestException(CommonErrorCode.BAD_REQUEST, message))
    }

    @ExceptionHandler
    fun handleBadRequest(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<*>> =
        failureResponse(BadRequestException(CommonErrorCode.BAD_REQUEST, resolveMessage(e)))

    @ExceptionHandler
    fun handleBadRequest(e: ServerWebInputException): ResponseEntity<ApiResponse<*>> {
        val missingParams = extractMissingParameter(e.reason ?: "")
        return if (missingParams.isNotEmpty()) {
            failureResponse(BadRequestException(CommonErrorCode.BAD_REQUEST, "필수 요청 값 '$missingParams'가 누락되었습니다."))
        } else {
            failureResponse(BadRequestException(CommonErrorCode.BAD_REQUEST))
        }
    }

    @ExceptionHandler
    fun handleNotFound(e: NoResourceFoundException): ResponseEntity<ApiResponse<*>> =
        failureResponse(NotFoundException(CommonErrorCode.NOT_FOUND))

    @ExceptionHandler
    fun handle(e: Throwable): ResponseEntity<ApiResponse<*>> {
        log.error("Exception : {}", e.message, e)
        return failureResponse(InternalServerException())
    }

    private fun failureResponse(exception: CoreException): ResponseEntity<ApiResponse<*>> {
        val status = resolveStatus(exception)
        return ResponseEntity(ApiResponse.fail(exception, status), status)
    }

    private fun resolveStatus(exception: CoreException): HttpStatus =
        when (exception) {
            is BadRequestException -> HttpStatus.BAD_REQUEST
            is UnauthorizedException -> HttpStatus.UNAUTHORIZED
            is ForbiddenException -> HttpStatus.FORBIDDEN
            is NotFoundException -> HttpStatus.NOT_FOUND
            is ConflictException -> HttpStatus.CONFLICT
            is InternalServerException -> HttpStatus.INTERNAL_SERVER_ERROR
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }

    private fun resolveMessage(e: HttpMessageNotReadableException): String =
        when (val rootCause = e.rootCause) {
            is InvalidFormatException -> {
                val fieldName = rootCause.path.joinToString(".") { it.fieldName ?: "?" }
                val expectedType = rootCause.targetType.simpleName
                val value = rootCause.value
                val valueIndicationMessage = if (rootCause.targetType.isEnum) {
                    val enumValues = rootCause.targetType.enumConstants.joinToString(", ") { it.toString() }
                    "사용 가능한 값 : [$enumValues]"
                } else {
                    ""
                }

                "필드 '$fieldName'의 값 '$value'이(가) 예상 타입($expectedType)과 일치하지 않습니다. $valueIndicationMessage"
            }

            is MismatchedInputException -> {
                val fieldPath = rootCause.path.joinToString(".") { it.fieldName ?: "?" }
                "필수 필드 '$fieldPath'이(가) 누락되었습니다."
            }

            is JsonMappingException -> {
                val fieldPath = rootCause.path.joinToString(".") { it.fieldName ?: "?" }
                "필드 '$fieldPath'에서 JSON 매핑 오류가 발생했습니다: ${rootCause.originalMessage}"
            }

            else -> "요청 본문을 처리하는 중 오류가 발생했습니다. JSON 메세지 규격을 확인해주세요."
        }

    private fun extractMissingParameter(message: String): String {
        val regex = "'(.+?)'".toRegex()
        return regex.find(message)?.groupValues?.get(1) ?: ""
    }
}
