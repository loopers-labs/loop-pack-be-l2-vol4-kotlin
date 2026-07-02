package com.loopers.domain.coupon.vo

import com.loopers.domain.coupon.constant.CouponErrorMessages
import com.loopers.domain.coupon.exception.InvalidCouponException
import com.loopers.domain.product.vo.Money

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
                throw InvalidCouponException(CouponErrorMessages.FIXED_AMOUNT_DISCOUNT_NOT_POSITIVE)
            }
        }
    }
}
