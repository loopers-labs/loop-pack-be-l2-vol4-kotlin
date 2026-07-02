package com.loopers.infrastructure.coupon.mapper

import com.loopers.domain.coupon.model.Coupon
import com.loopers.infrastructure.coupon.entity.CouponEntity

object CouponMapper {
    fun toDomain(coupon: CouponEntity): Coupon {
        return Coupon(
            id = coupon.id,
            name = coupon.name,
            type = coupon.type,
            discountValue = coupon.discountValue,
            minOrderAmount = coupon.minOrderAmount,
            expiredAt = coupon.expiredAt,
            isDeleted = coupon.isDeleted,
            issueLimit = coupon.issueLimit,
            issuedCount = coupon.issuedCount,
        )
    }

    fun toEntity(coupon: Coupon): CouponEntity {
        return CouponEntity(
            name = coupon.name,
            type = coupon.type,
            discountValue = coupon.discountValue,
            minOrderAmount = coupon.minOrderAmount,
            expiredAt = coupon.expiredAt,
            isDeleted = coupon.isDeleted,
            issueLimit = coupon.issueLimit,
            issuedCount = coupon.issuedCount,
        )
    }
}
