package com.loopers.support.error

import org.springframework.http.HttpStatus

open class InternalServerException(
    errorCode: ErrorCode = CommonErrorCode.INTERNAL_ERROR,
    customMessage: String? = null,
) : CoreException(HttpStatus.INTERNAL_SERVER_ERROR, errorCode, customMessage)
