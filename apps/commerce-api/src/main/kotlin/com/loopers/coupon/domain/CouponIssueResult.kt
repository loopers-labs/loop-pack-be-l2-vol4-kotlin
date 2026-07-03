package com.loopers.coupon.domain

import com.loopers.support.error.ConflictException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import java.time.LocalDateTime

@Entity
class CouponIssueResult(
    @Id
    @Column(name = "request_id", length = 36)
    val requestId: String,
    @Column(name = "coupon_id", nullable = false, updatable = false)
    val couponId: Long,
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,
    @Column(name = "requested_at", nullable = false, updatable = false)
    val requestedAt: LocalDateTime,
) {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CouponIssueResultStatus = CouponIssueResultStatus.PENDING
        protected set

    @Column(name = "user_coupon_id")
    var userCouponId: Long? = null
        protected set

    @Column(name = "reject_reason", length = 64)
    var rejectReason: String? = null
        protected set

    @Column(name = "decided_at")
    var decidedAt: LocalDateTime? = null
        protected set

    fun markIssued(userCouponId: Long, decidedAt: LocalDateTime) {
        requirePending()
        this.status = CouponIssueResultStatus.ISSUED
        this.userCouponId = userCouponId
        this.decidedAt = decidedAt
    }

    fun markRejected(rejectReason: String, decidedAt: LocalDateTime) {
        requirePending()
        this.status = CouponIssueResultStatus.REJECTED
        this.rejectReason = rejectReason
        this.decidedAt = decidedAt
    }

    private fun requirePending() {
        if (status != CouponIssueResultStatus.PENDING) {
            throw ConflictException(CouponErrorCode.ALREADY_DECIDED)
        }
    }
}

enum class CouponIssueResultStatus {
    PENDING,
    ISSUED,
    REJECTED,
}
