package com.loopers.domain.user.exception

open class UserDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
