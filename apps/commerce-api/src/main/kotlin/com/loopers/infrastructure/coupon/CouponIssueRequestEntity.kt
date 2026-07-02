package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "coupon_issue_request",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_coupon_issue_request_user_coupon",
            columnNames = ["user_id", "coupon_template_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_coupon_issue_request_user_coupon", columnList = "user_id, coupon_template_id"),
    ],
)
class CouponIssueRequestEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "coupon_template_id", nullable = false)
    val couponTemplateId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: PersistedCouponIssueStatus,

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 36)
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 32)
    var failureReason: PersistedCouponIssueFailureReason? = null,
) : BaseEntity() {
    fun toDomain(): CouponIssueRequest = CouponIssueRequest(
        id = id,
        userId = userId,
        couponTemplateId = couponTemplateId,
        status = status.toDomain(),
        idempotencyKey = idempotencyKey,
        failureReason = failureReason?.toDomain(),
    )

    companion object {
        fun from(domain: CouponIssueRequest): CouponIssueRequestEntity = CouponIssueRequestEntity(
            userId = domain.userId,
            couponTemplateId = domain.couponTemplateId,
            status = PersistedCouponIssueStatus.from(domain.status),
            idempotencyKey = domain.idempotencyKey,
            failureReason = domain.failureReason?.let { PersistedCouponIssueFailureReason.from(it) },
        )
    }
}
