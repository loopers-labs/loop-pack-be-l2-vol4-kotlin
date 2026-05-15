package com.loopers.support.error

open class ConflictException(
    errorCode: ErrorCode,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)
