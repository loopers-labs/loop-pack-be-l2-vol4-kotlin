package com.loopers.support.error

open class ForbiddenException(
    errorCode: ErrorCode = CommonErrorCode.FORBIDDEN,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)
