package com.loopers.domain.coupon.application.info

import com.loopers.domain.coupon.model.CouponTemplateModel
import com.loopers.domain.coupon.vo.FixedAmountDiscountPolicy
import com.loopers.domain.coupon.vo.PercentageDiscountPolicy
import java.time.LocalDateTime

data class CouponTemplateInfo(
    val id: Long,
    val name: String,
    val type: String,
    val value: Long,
    val minOrderAmount: Long,
    val expiredAt: LocalDateTime,
) {
    companion object {
        fun from(template: CouponTemplateModel): CouponTemplateInfo {
            val (type, value) = when (val policy = template.discountPolicy) {
                is FixedAmountDiscountPolicy -> "FIXED" to policy.amount.value
                is PercentageDiscountPolicy -> "RATE" to policy.percent.toLong()
            }
            return CouponTemplateInfo(
                id = template.id,
                name = template.name.value,
                type = type,
                value = value,
                minOrderAmount = template.minOrderAmount.value,
                expiredAt = template.expiredAt,
            )
        }
    }
}
