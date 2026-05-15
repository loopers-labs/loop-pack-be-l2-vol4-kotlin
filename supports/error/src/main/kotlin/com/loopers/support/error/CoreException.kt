package com.loopers.support.error

open class CoreException(
    val errorCode: ErrorCode,
    customMessage: String? = null,
) : RuntimeException(customMessage ?: errorCode.message)
