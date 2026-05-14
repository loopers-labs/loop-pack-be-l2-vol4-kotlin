package com.loopers.support.error

import org.springframework.http.HttpStatus

open class ForbiddenException(
    errorCode: ErrorCode = CommonErrorCode.FORBIDDEN,
    customMessage: String? = null,
) : CoreException(HttpStatus.FORBIDDEN, errorCode, customMessage)
