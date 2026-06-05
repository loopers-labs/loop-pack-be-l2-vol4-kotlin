package com.loopers.domain.product.exception

open class ProductDomainException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class InvalidProductException(
    message: String,
) : ProductDomainException(message)

class ProductNotOrderableException(
    productId: Long,
) : ProductDomainException("주문할 수 없는 상품입니다. productId=$productId")

class InsufficientStockException(
    productId: Long,
    requested: Long,
    available: Long,
) : ProductDomainException("재고가 부족합니다. productId=$productId, requested=$requested, available=$available")
