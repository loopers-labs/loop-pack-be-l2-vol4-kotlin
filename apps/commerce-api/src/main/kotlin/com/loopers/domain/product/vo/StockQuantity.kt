package com.loopers.domain.product.vo

import com.loopers.domain.product.constant.ProductErrorMessages
import com.loopers.domain.product.exception.InvalidProductException

@JvmInline
value class StockQuantity private constructor(
    val value: Long,
) {
    fun decrease(quantity: Quantity): StockQuantity = of(value - quantity.value)

    fun increase(quantity: Quantity): StockQuantity = of(value + quantity.value)

    companion object {
        fun of(value: Long): StockQuantity {
            validate(value)
            return StockQuantity(value)
        }

        private fun validate(value: Long) {
            if (value < 0) {
                throw InvalidProductException(ProductErrorMessages.STOCK_MUST_NOT_BE_NEGATIVE)
            }
        }
    }
}
