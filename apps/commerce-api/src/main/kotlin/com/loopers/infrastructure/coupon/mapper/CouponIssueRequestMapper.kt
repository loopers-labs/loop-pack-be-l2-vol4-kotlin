package com.loopers.infrastructure.coupon.mapper

import com.loopers.domain.coupon.model.CouponIssueRequest
import com.loopers.infrastructure.coupon.entity.CouponIssueRequestEntity

object CouponIssueRequestMapper {
    fun toDomain(entity: CouponIssueRequestEntity): CouponIssueRequest {
        return CouponIssueRequest(
            id = entity.id,
            requestId = entity.requestId,
            couponId = entity.couponId,
            memberId = entity.memberId,
            status = entity.status,
            issueId = entity.issueId,
            reason = entity.reason,
            requestedAt = entity.requestedAt,
        )
    }

    fun toEntity(request: CouponIssueRequest): CouponIssueRequestEntity {
        return CouponIssueRequestEntity(
            requestId = request.requestId,
            couponId = request.couponId,
            memberId = request.memberId,
            status = request.status,
            issueId = request.issueId,
            reason = request.reason,
            requestedAt = request.requestedAt,
        )
    }
}
