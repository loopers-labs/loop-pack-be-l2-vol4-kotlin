package com.loopers.domain.inventory.model

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

    fun deduct(quantity: Long) {
        if (quantity <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Deduct quantity must be positive.")
        }
        if (this.quantity < quantity) {
            throw CoreException(ErrorType.CONFLICT, "Inventory quantity is insufficient.")
        }

        this.quantity -= quantity
    }
}
