package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

// Hides: discount boundary validation, immutable snapshots, and retry idempotency.
@Entity
@Table(name = "orders")
class Order(
    val buyerId: Long,
    val originalAmount: Long,
) : BaseEntity() {
    var discountAmount: Long = 0
        protected set
    var finalAmount: Long = originalAmount
        protected set
    var appliedCouponId: Long? = null
        protected set
    var discountAppliedAt: Instant? = null
        protected set
    var confirmed: Boolean = false
        protected set

    init {
        if (buyerId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "buyerId must be positive")
        if (originalAmount < 0) throw CoreException(ErrorType.BAD_REQUEST, "originalAmount must not be negative")
    }

    fun applyDiscount(couponId: Long, discountAmount: Long, requestStartedAt: Instant) {
        appliedCouponId?.let {
            if (it == couponId) return
            throw CoreException(ErrorType.BAD_REQUEST, "only one coupon may be applied")
        }
        if (confirmed) throw CoreException(ErrorType.BAD_REQUEST, "a confirmed order cannot be discounted")
        if (couponId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "coupon is required")
        if (discountAmount < 0 || discountAmount > originalAmount) {
            throw CoreException(ErrorType.BAD_REQUEST, "discount must be within the original amount")
        }
        this.appliedCouponId = couponId
        this.discountAmount = discountAmount
        finalAmount = originalAmount - discountAmount
        discountAppliedAt = requestStartedAt
    }

    fun confirm() {
        confirmed = true
    }

    fun requireOwner(candidate: Long) {
        if (buyerId != candidate) throw CoreException(ErrorType.BAD_REQUEST, "order owner mismatch")
    }
}
