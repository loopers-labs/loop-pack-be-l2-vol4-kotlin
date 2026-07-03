package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "coupon_issue_requests",
    uniqueConstraints = [UniqueConstraint(name = "uk_coupon_issue_request_id", columnNames = ["request_id"])],
)
class CouponIssueRequest(
    requestId: String,
    userId: Long,
    couponId: Long,
) : BaseEntity() {
    @Column(name = "request_id", nullable = false, updatable = false, length = 36)
    var requestId: String = requestId
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long = couponId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: CouponIssueStatus = CouponIssueStatus.PENDING
        protected set

    @Column(name = "reason", length = 100)
    var reason: String? = null
        protected set

    fun markIssued() {
        status = CouponIssueStatus.ISSUED
        reason = null
    }

    fun markRejected(reason: String) {
        status = CouponIssueStatus.REJECTED
        this.reason = reason
    }

    fun isTerminal(): Boolean = status != CouponIssueStatus.PENDING
}
