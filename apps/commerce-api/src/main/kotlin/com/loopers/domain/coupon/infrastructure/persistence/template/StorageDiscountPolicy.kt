package com.loopers.domain.coupon.infrastructure.persistence.template

import com.loopers.domain.coupon.vo.CouponType

internal data class StorageDiscountPolicy(
    val type: CouponType,
    val value: Long,
)
