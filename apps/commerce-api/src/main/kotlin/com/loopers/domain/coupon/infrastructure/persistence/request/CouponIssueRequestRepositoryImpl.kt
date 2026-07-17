package com.loopers.domain.coupon.infrastructure.persistence.request

import com.loopers.domain.coupon.model.CouponIssueRequestModel
import com.loopers.domain.coupon.port.CouponIssueRequestRepository
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestRepositoryImpl(
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
) : CouponIssueRequestRepository {
    override fun save(request: CouponIssueRequestModel): CouponIssueRequestModel {
        val entity = if (request.id == 0L) {
            CouponIssueRequestJpaEntity.fromDomain(request)
        } else {
            couponIssueRequestJpaRepository.findById(request.id).orElseThrow()
                .also { it.updateFrom(request) }
        }
        return couponIssueRequestJpaRepository.saveAndFlush(entity).toDomain()
    }

    override fun insertIfAbsent(request: CouponIssueRequestModel): Int =
        couponIssueRequestJpaRepository.insertIfAbsent(
            requestId = request.requestId,
            userId = request.userId,
            couponTemplateId = request.couponTemplateId,
            status = request.status.name,
            requestedAt = request.requestedAt,
        )

    override fun findByRequestIdOrNull(requestId: UUID): CouponIssueRequestModel? =
        couponIssueRequestJpaRepository.findByRequestId(requestId)?.toDomain()

    override fun findByRequestIdAndUserIdOrNull(
        requestId: UUID,
        userId: Long,
    ): CouponIssueRequestModel? =
        couponIssueRequestJpaRepository.findByRequestIdAndUserId(requestId, userId)?.toDomain()

    override fun findByRequestIdForUpdateOrNull(requestId: UUID): CouponIssueRequestModel? =
        couponIssueRequestJpaRepository.findWithLockByRequestId(requestId)?.toDomain()

    override fun findByUserIdAndCouponTemplateIdOrNull(
        userId: Long,
        couponTemplateId: Long,
    ): CouponIssueRequestModel? =
        couponIssueRequestJpaRepository.findByUserIdAndCouponTemplateId(userId, couponTemplateId)?.toDomain()
}
