package com.loopers.support.error

import org.springframework.http.HttpStatus

open class ConflictException(
    errorCode: ErrorCode,
    customMessage: String? = null,
) : CoreException(HttpStatus.CONFLICT, errorCode, customMessage)
