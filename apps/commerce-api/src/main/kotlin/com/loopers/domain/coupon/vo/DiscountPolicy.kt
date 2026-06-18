package com.loopers.domain.coupon.vo

import com.loopers.domain.coupon.exception.InvalidCouponException
import com.loopers.domain.product.vo.Money

sealed interface DiscountPolicy {
    val couponType: CouponType

    fun calculate(totalPrice: Money): Money
}

class FixedAmountDiscountPolicy private constructor(
    val amount: Money,
) : DiscountPolicy {
    override val couponType: CouponType = CouponType.FIXED_AMOUNT

    override fun calculate(totalPrice: Money): Money =
        amount.coerceAtMost(totalPrice)

    companion object {
        fun of(amount: Money): FixedAmountDiscountPolicy {
            validate(amount)
            return FixedAmountDiscountPolicy(amount)
        }

        private fun validate(amount: Money) {
            if (amount.value <= 0) {
                throw InvalidCouponException("정액 할인 금액은 양수여야 합니다.")
            }
        }
    }
}

class PercentageDiscountPolicy private constructor(
    val percent: Int,
) : DiscountPolicy {
    override val couponType: CouponType = CouponType.PERCENTAGE

    override fun calculate(totalPrice: Money): Money =
        totalPrice.percent(percent).coerceAtMost(totalPrice)

    companion object {
        fun of(percent: Int): PercentageDiscountPolicy {
            validate(percent)
            return PercentageDiscountPolicy(percent)
        }

        private fun validate(percent: Int) {
            if (percent !in 1..100) {
                throw InvalidCouponException("정률 할인율은 1부터 100 사이여야 합니다.")
            }
        }
    }
}
