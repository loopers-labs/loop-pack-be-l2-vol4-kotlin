package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueRequest
import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueRequestJpaRepository : JpaRepository<CouponIssueRequest, Long> {
    fun findByRequestId(requestId: String): CouponIssueRequest?
}
