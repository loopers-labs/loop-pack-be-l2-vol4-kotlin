package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon

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
