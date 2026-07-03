package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

/**
 * 선착순 쿠폰 발급 요청과 그 결과.
 * requestId 로 결과를 조회(polling)하며, status 자체가 멱등 처리의 근거가 된다
 * (이미 PENDING 이 아니면 재처리하지 않는다).
 */
@Entity
@Table(name = "coupon_issue_request")
class CouponIssueRequestModel(
    requestId: String,
    userId: Long,
    couponId: Long,
) : BaseEntity() {
    @Column(name = "request_id", nullable = false, unique = true, length = 36)
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

    @Column(name = "reason", length = 200)
    var reason: String? = null
        protected set

    fun markSuccess() {
        status = CouponIssueStatus.SUCCESS
        reason = null
    }

    fun markFailed(reason: String) {
        status = CouponIssueStatus.FAILED
        this.reason = reason
    }
}
