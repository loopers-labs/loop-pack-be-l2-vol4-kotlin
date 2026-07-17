package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueRequestModel
import com.loopers.domain.coupon.CouponIssueRequestRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class CouponIssueRequestRepositoryImpl(
    private val jpaRepository: CouponIssueRequestJpaRepository,
) : CouponIssueRequestRepository {

    override fun findById(id: Long): CouponIssueRequestModel? {
        return jpaRepository.findByIdOrNull(id)
    }

    override fun save(model: CouponIssueRequestModel): CouponIssueRequestModel {
        return jpaRepository.save(model)
    }
}
