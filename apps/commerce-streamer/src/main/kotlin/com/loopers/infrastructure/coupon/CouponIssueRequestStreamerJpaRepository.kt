package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CouponIssueRequestStreamerJpaRepository : JpaRepository<CouponIssueRequestStreamerEntity, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): CouponIssueRequestStreamerEntity?

    @Modifying
    @Query(
        """
        UPDATE coupon_issue_request
        SET status = :#{#status.name()}, failure_reason = :#{#failureReason?.name()}, updated_at = NOW()
        WHERE user_id = :userId AND coupon_template_id = :couponTemplateId
        """,
        nativeQuery = true,
    )
    fun updateStatus(
        userId: Long,
        couponTemplateId: Long,
        status: PersistedStreamerCouponIssueStatus,
        failureReason: PersistedStreamerCouponIssueFailureReason?,
    )
}
