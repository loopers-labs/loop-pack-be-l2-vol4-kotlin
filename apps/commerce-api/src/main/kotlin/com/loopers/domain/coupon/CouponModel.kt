package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.ZonedDateTime
import kotlin.math.floor

@Entity
@Table(name = "coupon")
class CouponModel private constructor(
    name: String,
    type: CouponType,
    value: Double,
    minOrderAmount: Double,
    expiredAt: ZonedDateTime,
    quantity: Int,
) : BaseEntity() {
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    var type: CouponType = type
        protected set

    var value: Double = value
        protected set

    var minOrderAmount: Double = minOrderAmount
        protected set

    var expiredAt: ZonedDateTime = expiredAt
        protected set

    var quantity: Int = quantity
        protected set

    fun isApplicable(orderAmount: Double, now: ZonedDateTime): Boolean =
        orderAmount >= this.minOrderAmount && now.isBefore(this.expiredAt)

    fun discount(orderAmount: Double): Double {
        if (orderAmount < minOrderAmount) {
            throw IllegalArgumentException("orderAmount must be greater than minOrderAmount $minOrderAmount")
        }
        val raw = when (this.type) {
            CouponType.FIXED -> this.value
            CouponType.RATE -> orderAmount * this.value / 100.0
        }
        return floor(raw.coerceIn(0.0, orderAmount))
    }

    fun update(name: String, expiredAt: ZonedDateTime) {
        this.name = name
        this.expiredAt = expiredAt
    }

    fun isExpired(now: ZonedDateTime): Boolean = this.expiredAt.isBefore(now)

    companion object {
        fun of(
            name: String,
            type: CouponType,
            value: Double,
            minOrderAmount: Double,
            expiredAt: ZonedDateTime,
            quantity: Int,
        ): CouponModel = CouponModel(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
            quantity = quantity,
        )
    }
}
