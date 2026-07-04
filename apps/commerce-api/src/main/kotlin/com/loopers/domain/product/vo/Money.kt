package com.loopers.domain.product.vo

import com.loopers.domain.product.constant.ProductErrorMessages
import com.loopers.domain.product.exception.InvalidProductException
import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Money private constructor(
    val value: Long,
) : Comparable<Money> {
    operator fun plus(other: Money): Money = of(value + other.value)

    operator fun minus(other: Money): Money = of(value - other.value)

    operator fun times(quantity: Quantity): Money = of(value * quantity.value)

    fun percent(rate: Int): Money =
        of(
            BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(rate.toLong()))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR)
                .longValueExact(),
        )

    fun coerceAtMost(other: Money): Money = of(value.coerceAtMost(other.value))

    override fun compareTo(other: Money): Int = value.compareTo(other.value)

    companion object {
        val ZERO: Money = Money(0)

        fun of(value: Long): Money {
            validate(value)
            return Money(value)
        }

        private fun validate(value: Long) {
            if (value < 0) {
                throw InvalidProductException(ProductErrorMessages.PRICE_MUST_NOT_BE_NEGATIVE)
            }
        }
    }
}
