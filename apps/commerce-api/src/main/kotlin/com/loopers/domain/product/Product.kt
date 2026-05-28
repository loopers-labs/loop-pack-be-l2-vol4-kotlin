package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Product(
    val id: Long = 0L,
    val brandId: Long,
    name: String,
    price: Long,
    description: String,
    imageUrl: String,
    isDeleted: Boolean = false,
) {
    var name: String = name
        private set

    var price: Long = price
        private set

    var description: String = description
        private set

    var imageUrl: String = imageUrl
        private set

    var isDeleted: Boolean = isDeleted
        private set

    init {
        if (brandId <= 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Brand id must be positive.")
        }
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product name must not be blank.")
        }
        if (price < 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product price must not be negative.")
        }
        if (description.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product description must not be blank.")
        }
        if (imageUrl.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Product image url must not be blank.")
        }
    }

    fun ensureDisplayable() {
        if (isDeleted) {
            throw CoreException(ErrorType.NOT_FOUND, "Product not found.")
        }
    }
}
