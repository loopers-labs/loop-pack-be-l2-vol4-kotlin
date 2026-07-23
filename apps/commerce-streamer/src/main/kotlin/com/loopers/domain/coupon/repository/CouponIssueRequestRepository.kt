package com.loopers.domain.coupon.repository

import com.loopers.domain.coupon.model.CouponIssueRequest

interface CouponIssueRequestRepository {
    fun save(request: CouponIssueRequest): CouponIssueRequest

    fun findByRequestIdForUpdate(requestId: String): CouponIssueRequest?
}
