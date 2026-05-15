package com.loopers.support.error

open class NotFoundException(
    errorCode: ErrorCode,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)
