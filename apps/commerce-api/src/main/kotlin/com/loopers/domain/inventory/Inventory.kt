package com.loopers.domain.inventory

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Inventory(
    val id: Long = 0L,
    val productId: Long,
    quantity: Long,
) {
    var quantity: Long = quantity
        private set

    init {
        if (productId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product id must be positive.")
        }
        if (quantity < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Inventory quantity must not be negative.")
        }
    }
}
