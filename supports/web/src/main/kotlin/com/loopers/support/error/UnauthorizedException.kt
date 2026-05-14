package com.loopers.support.error

import org.springframework.http.HttpStatus

open class UnauthorizedException(
    errorCode: ErrorCode = CommonErrorCode.UNAUTHORIZED,
    customMessage: String? = null,
) : CoreException(HttpStatus.UNAUTHORIZED, errorCode, customMessage)
