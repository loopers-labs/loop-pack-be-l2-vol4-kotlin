package com.loopers.domain.coupon

interface CouponIssueRequestRepository {
    fun findByRequestId(requestId: String): IssueRequestRecord?

    fun save(record: IssueRequestRecord): IssueRequestRecord
}
