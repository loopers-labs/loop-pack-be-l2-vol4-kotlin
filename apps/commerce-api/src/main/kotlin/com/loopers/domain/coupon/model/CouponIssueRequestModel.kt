package com.loopers.domain.coupon.model

import java.time.ZonedDateTime
import java.util.UUID

data class CouponIssueRequestModel(
    val id: Long = 0L,
    val requestId: UUID = UUID.randomUUID(),
    val userId: Long,
    val couponTemplateId: Long,
    val status: CouponIssueRequestStatus = CouponIssueRequestStatus.PENDING,
    val issuedCouponId: Long? = null,
    val failureReason: String? = null,
    val requestedAt: ZonedDateTime = ZonedDateTime.now(),
    val completedAt: ZonedDateTime? = null,
) {
    fun markIssued(issuedCouponId: Long, now: ZonedDateTime = ZonedDateTime.now()): CouponIssueRequestModel =
        copy(status = CouponIssueRequestStatus.ISSUED, issuedCouponId = issuedCouponId, completedAt = now)

    fun markDuplicate(now: ZonedDateTime = ZonedDateTime.now()): CouponIssueRequestModel =
        copy(status = CouponIssueRequestStatus.DUPLICATE, completedAt = now)

    fun markSoldOut(reason: String?, now: ZonedDateTime = ZonedDateTime.now()): CouponIssueRequestModel =
        copy(status = CouponIssueRequestStatus.SOLD_OUT, failureReason = reason, completedAt = now)

    fun markFailed(reason: String?, now: ZonedDateTime = ZonedDateTime.now()): CouponIssueRequestModel =
        copy(status = CouponIssueRequestStatus.FAILED, failureReason = reason, completedAt = now)
}
