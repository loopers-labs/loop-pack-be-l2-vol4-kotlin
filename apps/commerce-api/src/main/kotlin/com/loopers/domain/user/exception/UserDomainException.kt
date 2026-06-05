package com.loopers.domain.user.exception

open class UserDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class InvalidUserException(
    message: String,
) : UserDomainException(message)

class InvalidPasswordException(
    message: String,
) : UserDomainException(message)
