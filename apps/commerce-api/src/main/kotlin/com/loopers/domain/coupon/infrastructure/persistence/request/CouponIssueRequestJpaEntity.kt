package com.loopers.domain.coupon.infrastructure.persistence.request

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.model.CouponIssueRequestModel
import com.loopers.domain.coupon.model.CouponIssueRequestStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime
import java.util.UUID

@Entity
@Table(
    name = "coupon_issue_requests",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_coupon_issue_requests_request_id", columnNames = ["request_id"]),
        UniqueConstraint(name = "uk_coupon_issue_requests_user_template", columnNames = ["user_id", "coupon_template_id"]),
    ],
    indexes = [
        Index(name = "idx_coupon_issue_requests_status", columnList = "request_status"),
    ],
)
class CouponIssueRequestJpaEntity(
    @Column(name = "request_id", nullable = false, updatable = false)
    var requestId: UUID,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "coupon_template_id", nullable = false)
    var couponTemplateId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false)
    var status: CouponIssueRequestStatus,
    @Column(name = "issued_coupon_id")
    var issuedCouponId: Long? = null,
    @Column(name = "failure_reason")
    var failureReason: String? = null,
    @Column(name = "requested_at", nullable = false)
    var requestedAt: ZonedDateTime,
    @Column(name = "completed_at")
    var completedAt: ZonedDateTime? = null,
) : BaseEntity() {
    fun updateFrom(request: CouponIssueRequestModel) {
        status = request.status
        issuedCouponId = request.issuedCouponId
        failureReason = request.failureReason
        completedAt = request.completedAt
    }

    fun toDomain(): CouponIssueRequestModel =
        CouponIssueRequestModel(
            id = id,
            requestId = requestId,
            userId = userId,
            couponTemplateId = couponTemplateId,
            status = status,
            issuedCouponId = issuedCouponId,
            failureReason = failureReason,
            requestedAt = requestedAt,
            completedAt = completedAt,
        )

    companion object {
        fun fromDomain(request: CouponIssueRequestModel): CouponIssueRequestJpaEntity =
            CouponIssueRequestJpaEntity(
                requestId = request.requestId,
                userId = request.userId,
                couponTemplateId = request.couponTemplateId,
                status = request.status,
                issuedCouponId = request.issuedCouponId,
                failureReason = request.failureReason,
                requestedAt = request.requestedAt,
                completedAt = request.completedAt,
            )
    }
}
