package com.loopers.domain.product.vo

import com.loopers.domain.product.constant.ProductErrorMessages
import com.loopers.domain.product.exception.InvalidProductException

@JvmInline
value class Quantity private constructor(
    val value: Long,
) {
    companion object {
        fun of(value: Long): Quantity {
            validate(value)
            return Quantity(value)
        }

        private fun validate(value: Long) {
            if (value <= 0) {
                throw InvalidProductException(ProductErrorMessages.QUANTITY_MUST_BE_POSITIVE)
            }
        }
    }
}
