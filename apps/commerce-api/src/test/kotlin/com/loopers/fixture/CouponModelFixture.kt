package com.loopers.fixture

import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import java.time.ZonedDateTime

data class CouponModelFixture(
    val name: String = "test쿠폰",
    val type: CouponType = CouponType.RATE,
    val value: Double = 50.0,
    val minOrderAmount: Double = 10000.0,
    val expiredAt: ZonedDateTime = ZonedDateTime.now().plusDays(5),
    val quantity: Int = 100,
) {
    fun toModel(): CouponModel = CouponModel.of(
        name = name,
        type = type,
        value = value,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
        quantity = quantity,
    )

    companion object {
        fun defaults() = CouponModelFixture()
    }
}
