package com.loopers.domain.like.exception

open class LikeDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
