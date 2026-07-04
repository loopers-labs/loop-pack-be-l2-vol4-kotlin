package com.loopers.domain.brand.exception

open class BrandDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
