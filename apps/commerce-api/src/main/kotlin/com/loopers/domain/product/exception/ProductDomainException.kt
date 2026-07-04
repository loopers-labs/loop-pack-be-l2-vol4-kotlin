package com.loopers.domain.product.exception

open class ProductDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
