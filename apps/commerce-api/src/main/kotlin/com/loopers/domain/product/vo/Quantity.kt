package com.loopers.domain.product.vo

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
                throw InvalidProductException("수량은 1개 이상이어야 합니다.")
            }
        }
    }
}
