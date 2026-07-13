package com.loopers.event

class NonRetryableEventException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
