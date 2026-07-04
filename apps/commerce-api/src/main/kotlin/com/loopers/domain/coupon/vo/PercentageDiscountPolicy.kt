package com.loopers.domain.coupon.vo

import com.loopers.domain.coupon.constant.CouponErrorMessages
import com.loopers.domain.coupon.exception.InvalidCouponException
import com.loopers.domain.product.vo.Money

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
                throw InvalidCouponException(CouponErrorMessages.PERCENTAGE_DISCOUNT_RATE_OUT_OF_RANGE)
            }
        }
    }
}
