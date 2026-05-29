package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class ProductSnapshot(
    val productId: Long,
    val productName: String,
    val productPrice: OrderItemPrice,
) {
    init {
        if (productId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 상품 ID 입니다.")
        if (productName.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.")
    }
}
