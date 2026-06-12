package com.loopers.domain.coupon.presentation.response

import com.loopers.domain.coupon.application.info.CouponTemplateInfo
import java.time.LocalDateTime

data class CouponTemplateResponse(
    val id: Long,
    val name: String,
    val type: String,
    val value: Long,
    val minOrderAmount: Long,
    val expiredAt: LocalDateTime,
) {
    companion object {
        fun from(info: CouponTemplateInfo): CouponTemplateResponse = CouponTemplateResponse(
            id = info.id,
            name = info.name,
            type = info.type,
            value = info.value,
            minOrderAmount = info.minOrderAmount,
            expiredAt = info.expiredAt,
        )
    }
}
