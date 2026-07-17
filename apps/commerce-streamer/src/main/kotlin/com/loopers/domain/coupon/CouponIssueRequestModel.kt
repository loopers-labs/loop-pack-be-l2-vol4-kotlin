package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "coupon_issue_requests",
    indexes = [
        Index(name = "idx_issue_req_user_coupon", columnList = "user_id, coupon_template_id"),
    ],
)
class CouponIssueRequestModel(
    userId: Long,
    couponTemplateId: Long,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "coupon_template_id", nullable = false)
    var couponTemplateId: Long = couponTemplateId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CouponIssueRequestStatus = CouponIssueRequestStatus.PENDING
        protected set

    @Column(name = "failure_reason")
    var failureReason: String? = null
        protected set

    fun markSuccess() {
        status = CouponIssueRequestStatus.SUCCESS
    }

    fun markFailed(reason: String) {
        status = CouponIssueRequestStatus.FAILED
        failureReason = reason
    }
}

enum class CouponIssueRequestStatus {
    PENDING,
    SUCCESS,
    FAILED,
}
