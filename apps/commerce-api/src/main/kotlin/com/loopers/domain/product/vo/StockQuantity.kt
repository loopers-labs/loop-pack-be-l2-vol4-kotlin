package com.loopers.domain.product.vo

import com.loopers.domain.product.exception.InvalidProductException

@JvmInline
value class StockQuantity private constructor(
    val value: Long,
) {
    fun decrease(quantity: Quantity): StockQuantity = of(value - quantity.value)

    companion object {
        fun of(value: Long): StockQuantity {
            validate(value)
            return StockQuantity(value)
        }

        private fun validate(value: Long) {
            if (value < 0) {
                throw InvalidProductException("재고는 음수일 수 없습니다.")
            }
        }
    }
}
