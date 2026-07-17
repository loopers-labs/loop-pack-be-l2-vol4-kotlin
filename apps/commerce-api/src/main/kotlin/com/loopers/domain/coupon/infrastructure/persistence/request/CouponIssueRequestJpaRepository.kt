package com.loopers.domain.coupon.infrastructure.persistence.request

import jakarta.persistence.LockModeType
import java.time.ZonedDateTime
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface CouponIssueRequestJpaRepository : JpaRepository<CouponIssueRequestJpaEntity, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            insert ignore into coupon_issue_requests (
                request_id,
                user_id,
                coupon_template_id,
                request_status,
                requested_at,
                created_at,
                updated_at
            ) values (
                :requestId,
                :userId,
                :couponTemplateId,
                :status,
                :requestedAt,
                :requestedAt,
                :requestedAt
            )
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        requestId: UUID,
        userId: Long,
        couponTemplateId: Long,
        status: String,
        requestedAt: ZonedDateTime,
    ): Int

    fun findByRequestId(requestId: UUID): CouponIssueRequestJpaEntity?

    fun findByRequestIdAndUserId(requestId: UUID, userId: Long): CouponIssueRequestJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByRequestId(requestId: UUID): CouponIssueRequestJpaEntity?

    fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): CouponIssueRequestJpaEntity?
}
