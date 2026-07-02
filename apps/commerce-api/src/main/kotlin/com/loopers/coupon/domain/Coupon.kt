package com.loopers.coupon.domain

import com.loopers.domain.BaseEntity
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
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
    // 선착순 발급 한도. null = 발급 한도 없음(관리자 지급 전용 쿠폰)
    @Column(name = "total_quantity", updatable = false)
    val totalQuantity: Long? = null,
) : BaseEntity() {

    @Column(name = "issued_quantity", nullable = false)
    var issuedQuantity: Long = 0
        protected set

    init {
        if (value <= 0) {
            throw BadRequestException(CouponErrorCode.INVALID_DISCOUNT_VALUE)
        }
        if (type == CouponType.RATE && value > 100) {
            throw BadRequestException(CouponErrorCode.RATE_DISCOUNT_OUT_OF_RANGE)
        }
        if (totalQuantity != null && totalQuantity <= 0) {
            throw BadRequestException(CouponErrorCode.INVALID_TOTAL_QUANTITY)
        }
    }

    fun isExpired(now: LocalDateTime): Boolean = this.expiredAt < now

    fun isSoldOut(): Boolean = totalQuantity != null && issuedQuantity >= totalQuantity

    fun issue(now: LocalDateTime) {
        if (totalQuantity == null) {
            throw BadRequestException(CouponErrorCode.NOT_ISSUABLE)
        }
        if (isExpired(now)) {
            throw BadRequestException(CouponErrorCode.EXPIRED)
        }
        if (isSoldOut()) {
            throw ConflictException(CouponErrorCode.SOLD_OUT)
        }
        issuedQuantity++
    }

    fun validateUsable(orderAmount: Long, now: LocalDateTime) {
        if (Money(orderAmount).amount < minOrderAmount.amount) {
            throw BadRequestException(CouponErrorCode.MIN_ORDER_NOT_MET)
        }
        if (isExpired(now)) {
            throw BadRequestException(CouponErrorCode.EXPIRED)
        }
    }
}
