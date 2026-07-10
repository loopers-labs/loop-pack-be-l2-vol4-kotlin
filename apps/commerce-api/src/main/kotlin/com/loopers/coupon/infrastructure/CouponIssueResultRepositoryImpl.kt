package com.loopers.coupon.infrastructure

import com.loopers.coupon.domain.CouponIssueResult
import com.loopers.coupon.domain.CouponIssueResultRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class CouponIssueResultRepositoryImpl(
    val couponIssueResultJpaRepository: CouponIssueResultJpaRepository,
) : CouponIssueResultRepository {
    override fun save(couponIssueResult: CouponIssueResult): CouponIssueResult =
        couponIssueResultJpaRepository.save(couponIssueResult)

    override fun findById(requestId: String): CouponIssueResult? =
        couponIssueResultJpaRepository.findByIdOrNull(requestId)
}

interface CouponIssueResultJpaRepository : JpaRepository<CouponIssueResult, String>
