package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "coupons")
class Coupon(
    @Column(name = "name", nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: CouponType,

    @Column(name = "value", nullable = false)
    var value: Long,

    @Column(name = "min_order_amount")
    var minOrderAmount: Long? = null,

    @Column(name = "expired_at", nullable = false)
    var expiredAt: LocalDateTime,
) : BaseEntity() {
    init {
        validate()
    }

    override fun guard() {
        validate()
    }

    fun change(
        name: String,
        type: CouponType,
        value: Long,
        minOrderAmount: Long?,
        expiredAt: LocalDateTime,
    ) {
        this.name = name
        this.type = type
        this.value = value
        this.minOrderAmount = minOrderAmount
        this.expiredAt = expiredAt
        validate()
    }

    fun isExpired(now: LocalDateTime): Boolean = !expiredAt.isAfter(now)

    fun calculateDiscount(orderAmount: Long): Long {
        if (orderAmount < 0) throw CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 미만일 수 없습니다.")
        val minimum = minOrderAmount
        if (minimum != null && orderAmount < minimum) {
            throw CoreException(ErrorType.CONFLICT, "쿠폰 최소 주문 금액을 충족하지 못했습니다.")
        }
        val discount = when (type) {
            CouponType.FIXED -> value
            CouponType.RATE -> orderAmount * value / 100
        }
        return minOf(discount, orderAmount)
    }

    private fun validate() {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 비어있을 수 없습니다.")
        if (value <= 0) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 값은 0보다 커야 합니다.")
        if (type == CouponType.RATE && value !in 1..100) {
            throw CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰 값은 1 이상 100 이하이어야 합니다.")
        }
        val minimum = minOrderAmount
        if (minimum != null && minimum < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 미만일 수 없습니다.")
        }
    }
}
