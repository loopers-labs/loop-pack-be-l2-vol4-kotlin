package com.loopers.domain.coupon.port

import com.loopers.domain.coupon.model.CouponIssueRequestModel
import java.util.UUID

interface CouponIssueRequestRepository {
    fun save(request: CouponIssueRequestModel): CouponIssueRequestModel

    fun findByRequestIdOrNull(requestId: UUID): CouponIssueRequestModel?

    fun findByRequestIdAndUserIdOrNull(requestId: UUID, userId: Long): CouponIssueRequestModel?

    fun findByRequestIdForUpdateOrNull(requestId: UUID): CouponIssueRequestModel?

    fun findByUserIdAndCouponTemplateIdOrNull(userId: Long, couponTemplateId: Long): CouponIssueRequestModel?
}
