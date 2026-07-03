package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueRequestModel
import com.loopers.domain.coupon.CouponIssueRequestRepository
import org.springframework.stereotype.Repository

@Repository
class CouponIssueRequestRepositoryImpl(
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
) : CouponIssueRequestRepository {
    override fun save(request: CouponIssueRequestModel): CouponIssueRequestModel =
        couponIssueRequestJpaRepository.save(request)

    override fun findByRequestId(requestId: String): CouponIssueRequestModel? =
        couponIssueRequestJpaRepository.findByRequestId(requestId)
}
