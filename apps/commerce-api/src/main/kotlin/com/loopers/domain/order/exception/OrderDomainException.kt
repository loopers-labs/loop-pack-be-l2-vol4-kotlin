package com.loopers.domain.order.exception

open class OrderDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
