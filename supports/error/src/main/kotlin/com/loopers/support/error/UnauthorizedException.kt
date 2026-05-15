package com.loopers.support.error

open class UnauthorizedException(
    errorCode: ErrorCode = CommonErrorCode.UNAUTHORIZED,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)
