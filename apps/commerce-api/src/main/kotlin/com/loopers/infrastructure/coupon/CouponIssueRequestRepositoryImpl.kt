package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueRequestModel
import com.loopers.domain.coupon.CouponIssueRequestRepository
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestRepositoryImpl(
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository
) : CouponIssueRequestRepository {
    override fun save(couponIssueRequestModel: CouponIssueRequestModel): CouponIssueRequestModel =
        couponIssueRequestJpaRepository.save(couponIssueRequestModel)

    override fun findByRequestId(requestId: String): CouponIssueRequestModel? =
        couponIssueRequestJpaRepository.findByRequestId(requestId)
}
