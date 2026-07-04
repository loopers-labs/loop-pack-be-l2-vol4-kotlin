package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table


@Entity
@Table(name = "coupon_issue_request")
class CouponIssueRequestModel private constructor(
    requestId: String,
    couponId: Long,
    userId: Long,
) : BaseEntity() {
    @Column(unique = true)
    var requestId: String = requestId
        protected set

    var couponId: Long = couponId
        protected set

    var userId: Long = userId
        protected set

    var status: CouponIssueStatus = CouponIssueStatus.PENDING
        protected set

    fun markSuccess() {
        if (status == CouponIssueStatus.PENDING) status = CouponIssueStatus.SUCCESS
    }

    fun markSoldOut() {
        if (status == CouponIssueStatus.PENDING) status = CouponIssueStatus.SOLD_OUT
    }

    fun markFailed() {
        if (status == CouponIssueStatus.PENDING) status = CouponIssueStatus.FAILED
    }

    companion object {
        fun of(requestId: String, couponId: Long, userId: Long) =
            CouponIssueRequestModel(requestId = requestId, couponId = couponId, userId = userId)
    }
}
