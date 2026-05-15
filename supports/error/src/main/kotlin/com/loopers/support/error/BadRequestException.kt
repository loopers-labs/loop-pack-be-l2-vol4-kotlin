package com.loopers.support.error

open class BadRequestException(
    errorCode: ErrorCode,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)
