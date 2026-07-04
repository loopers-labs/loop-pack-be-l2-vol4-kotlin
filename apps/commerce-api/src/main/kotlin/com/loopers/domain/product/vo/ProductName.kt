package com.loopers.domain.product.vo

import com.loopers.domain.product.constant.ProductErrorMessages
import com.loopers.domain.product.exception.InvalidProductException

@JvmInline
value class ProductName private constructor(
    val value: String,
) {
    companion object {
        fun of(value: String): ProductName {
            validate(value)
            return ProductName(value)
        }

        private fun validate(value: String) {
            if (value.isBlank()) {
                throw InvalidProductException(ProductErrorMessages.PRODUCT_NAME_MUST_NOT_BE_BLANK)
            }
        }
    }
}
