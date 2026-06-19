package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

sealed class DiscountPolicy {

    enum class Type {
        FIXED_AMOUNT,
        RATE,
    }

    abstract val type: Type

    abstract fun discountOf(targetAmount: DiscountAmount): DiscountAmount

    class FixedAmount(val amount: Long) : DiscountPolicy() {
        override val type: Type = Type.FIXED_AMOUNT

        init {
            if (amount <= 0) {
                throw CoreException(ErrorType.BAD_REQUEST, "정액 할인액은 0보다 커야 합니다.")
            }
        }

        override fun discountOf(targetAmount: DiscountAmount): DiscountAmount {
            val capped = minOf(amount, targetAmount.amount)
            return DiscountAmount(capped)
        }
    }

    class Rate(val percent: Int) : DiscountPolicy() {
        override val type: Type = Type.RATE

        init {
            if (percent !in 0..100) {
                throw CoreException(ErrorType.BAD_REQUEST, "정률 할인율은 0 이상 100 이하여야 합니다.")
            }
        }

        override fun discountOf(targetAmount: DiscountAmount): DiscountAmount {
            val discount = targetAmount.amount * percent / 100
            return DiscountAmount(discount)
        }
    }
}
