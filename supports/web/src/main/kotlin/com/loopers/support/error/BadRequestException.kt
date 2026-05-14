package com.loopers.support.error

import org.springframework.http.HttpStatus

open class BadRequestException(
    errorCode: ErrorCode,
    customMessage: String? = null,
) : CoreException(HttpStatus.BAD_REQUEST, errorCode, customMessage)
