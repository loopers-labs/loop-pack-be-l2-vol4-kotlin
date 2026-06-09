package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.DiscountType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "coupon")
class CouponEntity(
    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "type", nullable = false)
    var type: DiscountType,

    @Column(name = "discount_value", nullable = false)
    var discountValue: Long,

    @Column(name = "min_order_amount")
    var minOrderAmount: Long?,

    @Column(name = "expired_at", nullable = false)
    var expiredAt: ZonedDateTime,

    @Column(name = "is_deleted", nullable = false)
    var isDeleted: Boolean,
) : BaseEntity()
