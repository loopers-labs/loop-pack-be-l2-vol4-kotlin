package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class OrderItem(
    val id: Long = 0L,
    val productId: Long,
    val productName: String,
    val brandName: String,
    val unitPrice: Long,
    val quantity: Long,
    val totalAmount: Long,
) {
    init {
        if (productId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product id must be positive.")
        }
        if (productName.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product name must not be blank.")
        }
        if (brandName.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Brand name must not be blank.")
        }
        if (unitPrice < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Unit price must not be negative.")
        }
        if (quantity <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order item quantity must be positive.")
        }
        if (totalAmount != unitPrice * quantity) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order item total amount is invalid.")
        }
    }

    companion object {
        fun snapshot(
            productId: Long,
            productName: String,
            brandName: String,
            unitPrice: Long,
            quantity: Long,
        ): OrderItem {
            return OrderItem(
                productId = productId,
                productName = productName,
                brandName = brandName,
                unitPrice = unitPrice,
                quantity = quantity,
                totalAmount = unitPrice * quantity,
            )
        }
    }
}
