package com.loopers.infrastructure.coupon.mapper

import com.loopers.domain.coupon.model.Coupon
import com.loopers.infrastructure.coupon.entity.CouponEntity

object CouponMapper {
    fun toDomain(coupon: CouponEntity): Coupon {
        return Coupon(
            coupon.id,
            coupon.name,
            coupon.type,
            coupon.discountValue,
            coupon.minOrderAmount,
            coupon.expiredAt,
            coupon.isDeleted,
        )
    }

    fun toEntity(coupon: Coupon): CouponEntity {
        return CouponEntity(
            coupon.name,
            coupon.type,
            coupon.discountValue,
            coupon.minOrderAmount,
            coupon.expiredAt,
            coupon.isDeleted,
        )
    }
}
