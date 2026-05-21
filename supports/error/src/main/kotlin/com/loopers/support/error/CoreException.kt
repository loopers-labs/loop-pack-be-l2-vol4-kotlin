package com.loopers.support.error

abstract class CoreException(
    val errorCode: ErrorCode,
    customMessage: String? = null,
) : RuntimeException(customMessage ?: errorCode.message)

open class BadRequestException(
    errorCode: ErrorCode,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)

open class UnauthorizedException(
    errorCode: ErrorCode = CommonErrorCode.UNAUTHORIZED,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)

open class ForbiddenException(
    errorCode: ErrorCode = CommonErrorCode.FORBIDDEN,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)

open class NotFoundException(
    errorCode: ErrorCode,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)

open class ConflictException(
    errorCode: ErrorCode,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)

open class InternalServerException(
    errorCode: ErrorCode = CommonErrorCode.INTERNAL_ERROR,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)
