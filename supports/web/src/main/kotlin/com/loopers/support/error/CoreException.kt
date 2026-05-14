package com.loopers.support.error

import org.springframework.http.HttpStatus

open class CoreException(
    val status: HttpStatus,
    val errorCode: ErrorCode,
    customMessage: String? = null,
) : RuntimeException(customMessage ?: errorCode.message)
