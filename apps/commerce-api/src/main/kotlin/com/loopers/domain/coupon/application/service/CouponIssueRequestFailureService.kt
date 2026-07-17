package com.loopers.domain.coupon.application.service

import com.loopers.domain.coupon.model.CouponIssueRequestStatus
import com.loopers.domain.coupon.port.CouponIssueRequestRepository
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class CouponIssueRequestFailureService(
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(requestId: UUID, reason: String): CouponIssueRequestStatus? {
        val request = couponIssueRequestRepository.findByRequestIdForUpdateOrNull(requestId)
            ?: return null
        if (request.status != CouponIssueRequestStatus.PENDING) {
            return request.status
        }
        return couponIssueRequestRepository.save(request.markFailed(reason.take(MAX_FAILURE_REASON_LENGTH))).status
    }

    private companion object {
        const val MAX_FAILURE_REASON_LENGTH = 255
    }
}
