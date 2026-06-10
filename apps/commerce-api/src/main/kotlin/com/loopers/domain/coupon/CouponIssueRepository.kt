package com.loopers.domain.coupon

import org.springframework.data.domain.Page

interface CouponIssueRepository {
    fun save(issue: CouponIssue): CouponIssue

    fun findById(issueId: Long): CouponIssue?

    fun findAllByCouponId(couponId: Long, page: Int, size: Int): Page<CouponIssue>

    fun existsByCouponId(couponId: Long): Boolean
}
