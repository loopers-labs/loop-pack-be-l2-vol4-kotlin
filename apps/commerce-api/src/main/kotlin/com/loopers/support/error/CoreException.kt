package com.loopers.support.error

class CoreException(
    val errorType: ErrorType,
    val customMessage: String? = null,
    cause: Throwable? = null,
) : RuntimeException(customMessage ?: errorType.message, cause)
