package com.loopers.support.error

import org.springframework.http.HttpStatus

open class NotFoundException(
    errorCode: ErrorCode,
    customMessage: String? = null,
) : CoreException(HttpStatus.NOT_FOUND, errorCode, customMessage)
