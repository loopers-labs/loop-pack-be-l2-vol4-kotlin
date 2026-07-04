package com.loopers.domain.coupon.infrastructure.persistence.request

import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface CouponIssueRequestJpaRepository : JpaRepository<CouponIssueRequestJpaEntity, Long> {
    fun findByRequestId(requestId: UUID): CouponIssueRequestJpaEntity?

    fun findByRequestIdAndUserId(requestId: UUID, userId: Long): CouponIssueRequestJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByRequestId(requestId: UUID): CouponIssueRequestJpaEntity?

    fun findByUserIdAndCouponTemplateId(userId: Long, couponTemplateId: Long): CouponIssueRequestJpaEntity?
}
