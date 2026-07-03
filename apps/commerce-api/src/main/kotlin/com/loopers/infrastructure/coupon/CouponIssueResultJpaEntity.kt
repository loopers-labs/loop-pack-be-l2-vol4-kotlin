package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.CouponIssueResult
import com.loopers.domain.coupon.CouponIssueStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "coupon_issue_results",
    indexes = [
        Index(name = "idx_cir_request_id", columnList = "request_id", unique = true),
        Index(name = "idx_cir_user_coupon", columnList = "user_id, coupon_id"),
    ],
)
class CouponIssueResultJpaEntity(
    @Column(name = "request_id", nullable = false, length = 36)
    val requestId: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "coupon_id", nullable = false)
    val couponId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: CouponIssueStatus,

    @Column(name = "reason", length = 200)
    var reason: String? = null,
) : BaseEntity() {
    fun toDomain(): CouponIssueResult = CouponIssueResult(
        id = id,
        requestId = requestId,
        userId = userId,
        couponId = couponId,
        status = status,
        reason = reason,
    )

    fun updateFrom(result: CouponIssueResult) {
        status = result.status
        reason = result.reason
    }

    companion object {
        fun from(result: CouponIssueResult): CouponIssueResultJpaEntity = CouponIssueResultJpaEntity(
            requestId = result.requestId,
            userId = result.userId,
            couponId = result.couponId,
            status = result.status,
            reason = result.reason,
        )
    }
}
