package com.loopers.domain.product.exception

class ProductNotOrderableException(
    productId: Long,
) : ProductDomainException("주문할 수 없는 상품입니다. productId=$productId")
