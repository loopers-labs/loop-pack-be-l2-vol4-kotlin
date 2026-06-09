package com.loopers.application.product.dto

import com.loopers.domain.product.ProductSort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class ProductListCommand(
    val brandId: Long?,
    val sort: ProductSort,
    val page: Int,
    val size: Int,
) {
    init {
        if (page < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "page must not be negative.")
        }
        if (size <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "size must be positive.")
        }
    }
}
