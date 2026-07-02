package com.loopers.infrastructure.coupon.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.enums.CouponIssueRequestStatus
import com.loopers.domain.coupon.model.CouponIssueRequest
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(
    name = "coupon_issue_request",
    indexes = [
        Index(name = "uk_coupon_issue_request_request_id", columnList = "request_id", unique = true),
    ],
)
class CouponIssueRequestEntity(
    @Column(name = "request_id", nullable = false, unique = true)
    var requestId: String,

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CouponIssueRequestStatus,

    @Column(name = "issue_id")
    var issueId: Long?,

    @Column(name = "reason")
    var reason: String?,

    @Column(name = "requested_at", nullable = false)
    var requestedAt: ZonedDateTime,
) : BaseEntity() {
    fun update(request: CouponIssueRequest) {
        requestId = request.requestId
        couponId = request.couponId
        memberId = request.memberId
        status = request.status
        issueId = request.issueId
        reason = request.reason
        requestedAt = request.requestedAt
    }
}
