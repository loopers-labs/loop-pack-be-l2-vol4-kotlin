package com.loopers.support.error

open class InternalServerException(
    errorCode: ErrorCode = CommonErrorCode.INTERNAL_ERROR,
    customMessage: String? = null,
) : CoreException(errorCode, customMessage)
