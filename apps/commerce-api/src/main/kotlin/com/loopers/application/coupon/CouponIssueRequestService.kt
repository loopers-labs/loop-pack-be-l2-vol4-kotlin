package com.loopers.application.coupon

import com.loopers.domain.coupon.model.CouponIssueRequest
import com.loopers.domain.coupon.repository.CouponIssueRequestRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponIssueRequestService(
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
) {
    @Transactional
    fun createRequested(couponId: Long, memberId: Long): CouponIssueRequest {
        return CouponIssueRequest.requested(couponId = couponId, memberId = memberId)
            .let(couponIssueRequestRepository::save)
    }

    @Transactional(readOnly = true)
    fun getOwnedRequest(requestId: String, memberId: Long): CouponIssueRequest {
        val request = couponIssueRequestRepository.findByRequestId(requestId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Coupon issue request not found.")

        if (request.memberId != memberId) {
            throw CoreException(ErrorType.NOT_FOUND, "Coupon issue request not found.")
        }

        return request
    }
}
