package com.loopers.domain.order.dto

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class OrderPlacementItem(
    val productId: Long,
    val quantity: Long,
) {
    init {
        if (productId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product id must be positive.")
        }
        if (quantity <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order quantity must be positive.")
        }
    }
}
