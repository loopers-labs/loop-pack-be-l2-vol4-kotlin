package com.loopers.infrastructure.coupon.repository

import com.loopers.domain.coupon.model.CouponIssueRequest
import com.loopers.domain.coupon.repository.CouponIssueRequestRepository
import com.loopers.infrastructure.coupon.entity.CouponIssueRequestEntity
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestRepositoryImpl(
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
) : CouponIssueRequestRepository {
    override fun save(request: CouponIssueRequest): CouponIssueRequest {
        val entity = couponIssueRequestJpaRepository.findByIdOrNull(request.id)
            ?.also { it.update(request) }
            ?: error("Coupon issue request not found: ${request.requestId}")

        return couponIssueRequestJpaRepository.save(entity)
            .toDomain()
    }

    override fun findByRequestIdForUpdate(requestId: String): CouponIssueRequest? {
        return couponIssueRequestJpaRepository.findByRequestIdForUpdate(requestId)
            ?.toDomain()
    }

    private fun CouponIssueRequestEntity.toDomain(): CouponIssueRequest {
        return CouponIssueRequest(
            id = id,
            requestId = requestId,
            couponId = couponId,
            memberId = memberId,
            status = status,
            issueId = issueId,
            reason = reason,
            requestedAt = requestedAt,
        )
    }
}
