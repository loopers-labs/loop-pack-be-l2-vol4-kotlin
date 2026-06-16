package com.loopers.coupon.domain

import com.loopers.domain.BaseEntity
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.LocalDateTime

@Entity
class Coupon(
    @Column(name = "type", nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    val type: CouponType,
    @Column(name = "name", nullable = false, updatable = false)
    val name: String,
    @Column(name = "discount_value", nullable = false, updatable = false)
    val value: Long,
    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "min_order_amount", nullable = false, updatable = false))
    val minOrderAmount: Money,
    @Column(name = "expired_at", nullable = false, updatable = false)
    val expiredAt: LocalDateTime,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: Long,
) : BaseEntity() {

    init {
        if (value <= 0) {
            throw BadRequestException(CouponErrorCode.INVALID_DISCOUNT_VALUE)
        }
        if (type == CouponType.RATE && value > 100) {
            throw BadRequestException(CouponErrorCode.RATE_DISCOUNT_OUT_OF_RANGE)
        }
    }

    fun isExpired(now: LocalDateTime): Boolean = this.expiredAt < now

    fun validateUsable(orderAmount: Money, now: LocalDateTime) {
        if (orderAmount.amount < minOrderAmount.amount) {
            throw BadRequestException(CouponErrorCode.MIN_ORDER_NOT_MET)
        }
        if (isExpired(now)) {
            throw BadRequestException(CouponErrorCode.EXPIRED)
        }
    }
}
