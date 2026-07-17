package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "coupon_templates")
class CouponTemplateModel(
    name: String,
    type: CouponType,
    value: Long,
    minOrderAmount: Long?,
    expiredAt: ZonedDateTime,
    totalQuantity: Long? = null,
) : BaseEntity() {

    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: CouponType = type
        protected set

    @Column(name = "value", nullable = false)
    var value: Long = value
        protected set

    @Column(name = "min_order_amount")
    var minOrderAmount: Long? = minOrderAmount
        protected set

    @Column(name = "expired_at", nullable = false)
    var expiredAt: ZonedDateTime = expiredAt
        protected set

    @Column(name = "total_quantity")
    var totalQuantity: Long? = totalQuantity
        protected set

    @Column(name = "issued_count", nullable = false)
    var issuedCount: Long = 0
        protected set

    fun issueOne() {
        if (totalQuantity != null && issuedCount >= totalQuantity!!) {
            throw IllegalStateException("쿠폰 발급 수량이 모두 소진되었습니다.")
        }
        issuedCount++
    }

    fun hasRemainingQuantity(): Boolean =
        totalQuantity == null || issuedCount < totalQuantity!!
}

enum class CouponType {
    FIXED,
    RATE,
}
