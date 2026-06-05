package com.loopers.domain.product.vo

import com.loopers.domain.product.exception.InvalidProductException

@JvmInline
value class Money private constructor(
    val value: Long,
) {
    companion object {
        fun of(value: Long): Money {
            validate(value)
            return Money(value)
        }

        private fun validate(value: Long) {
            if (value < 0) {
                throw InvalidProductException("가격은 음수일 수 없습니다.")
            }
        }
    }
}
