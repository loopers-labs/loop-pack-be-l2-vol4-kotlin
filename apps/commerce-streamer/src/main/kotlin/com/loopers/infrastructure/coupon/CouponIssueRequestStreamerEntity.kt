package com.loopers.infrastructure.coupon

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

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
class CouponIssueRequestStreamerEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "coupon_template_id", nullable = false)
    val couponTemplateId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: PersistedStreamerCouponIssueStatus,

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 36)
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 32)
    var failureReason: PersistedStreamerCouponIssueFailureReason? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: ZonedDateTime = ZonedDateTime.now(),

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
)
