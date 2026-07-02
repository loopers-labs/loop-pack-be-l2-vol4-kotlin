package com.loopers.domain.product.exception

class InsufficientStockException(
    productId: Long,
    requested: Long,
    available: Long,
) : ProductDomainException("재고가 부족합니다. productId=$productId, requested=$requested, available=$available")
