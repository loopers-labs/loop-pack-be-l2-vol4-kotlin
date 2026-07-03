package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime

@Entity
@Table(name = "coupons")
class CouponModel(
    name: String,
    type: CouponType,
    discountValue: BigDecimal,
    minOrderAmount: BigDecimal?,
    expiredAt: ZonedDateTime,
    totalQuantity: Int? = null,
    issuedCount: Int = 0,
) : BaseEntity() {
    @Column(nullable = false, length = 200)
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var type: CouponType = type
        protected set

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    var discountValue: BigDecimal = discountValue
        protected set

    @Column(name = "min_order_amount", precision = 12, scale = 2)
    var minOrderAmount: BigDecimal? = minOrderAmount
        protected set

    @Column(name = "expired_at", nullable = false)
    var expiredAt: ZonedDateTime = expiredAt
        protected set

    @Column(name = "total_quantity")
    var totalQuantity: Int? = totalQuantity
        protected set

    @Column(name = "issued_count", nullable = false)
    var issuedCount: Int = issuedCount
        protected set

    init {
        validate(name = name, type = type, discountValue = discountValue, minOrderAmount = minOrderAmount)
    }

    fun calculateDiscount(orderAmount: BigDecimal): BigDecimal {
        minOrderAmount?.let {
            if (orderAmount < it) throw CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 만족하지 않습니다.")
        }
        return when (type) {
            CouponType.FIXED -> discountValue.min(orderAmount)
            CouponType.RATE -> orderAmount.multiply(discountValue)
                .divide(BigDecimal(100))
                .setScale(2, RoundingMode.DOWN)
        }
    }

    fun isExpired(now: ZonedDateTime): Boolean {
        return expiredAt.isBefore(now)
    }

    /**
     * 선착순 슬롯을 확보한다. (비-DB 경로용 — 실제 동시성 하에서는 CouponRepository.claimIssueSlot의 원자적 UPDATE를 사용한다.)
     */
    fun claimIssueSlot(): Boolean {
        val total = totalQuantity
        if (total != null && issuedCount >= total) return false
        issuedCount += 1
        return true
    }

    fun update(
        name: String,
        type: CouponType,
        discountValue: BigDecimal,
        minOrderAmount: BigDecimal?,
        expiredAt: ZonedDateTime,
    ) {
        validate(name = name, type = type, discountValue = discountValue, minOrderAmount = minOrderAmount)
        this.name = name
        this.type = type
        this.discountValue = discountValue
        this.minOrderAmount = minOrderAmount
        this.expiredAt = expiredAt
    }

    companion object {
        private fun validate(name: String, type: CouponType, discountValue: BigDecimal, minOrderAmount: BigDecimal?) {
            if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 비어있을 수 없습니다.")
            if (name.length > 200) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 200자를 초과할 수 없습니다.")
            when (type) {
                CouponType.FIXED ->
                    if (discountValue <= BigDecimal.ZERO) {
                        throw CoreException(ErrorType.BAD_REQUEST, "정액 할인 금액은 0보다 커야 합니다.")
                    }
                CouponType.RATE ->
                    if (discountValue < BigDecimal.ONE || discountValue > BigDecimal(100)) {
                        throw CoreException(ErrorType.BAD_REQUEST, "정률 할인율은 1~100 사이여야 합니다.")
                    }
            }
            if (minOrderAmount != null && minOrderAmount <= BigDecimal.ZERO) {
                throw CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0보다 커야 합니다.")
            }
        }
    }
}
