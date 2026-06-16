package com.loopers.infrastructure.coupon.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.enums.CouponIssueStatus
import com.loopers.domain.coupon.enums.DiscountType
import com.loopers.domain.coupon.model.CouponIssue
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "coupon_issue")
class CouponIssueEntity(
    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CouponIssueStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: DiscountType,

    @Column(name = "discount_value", nullable = false)
    var discountValue: Long,

    @Column(name = "min_order_amount")
    var minOrderAmount: Long?,

    @Column(name = "expired_at", nullable = false)
    var expiredAt: ZonedDateTime,

    @Column(name = "used_at")
    var usedAt: ZonedDateTime?,
) : BaseEntity() {
    fun update(issue: CouponIssue) {
        memberId = issue.memberId
        couponId = issue.couponId
        status = issue.status
        type = issue.type
        discountValue = issue.discountValue
        minOrderAmount = issue.minOrderAmount
        expiredAt = issue.expiredAt
        usedAt = issue.usedAt
    }
}
