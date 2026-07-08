package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class CouponIssueRequestService(
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
) {
    @Transactional
    fun create(requestId: String, userId: Long, couponId: Long): CouponIssueRequestModel =
        couponIssueRequestRepository.save(CouponIssueRequestModel(requestId, userId, couponId))

    @Transactional
    fun markFailed(requestId: String, reason: String) {
        couponIssueRequestRepository.findByRequestId(requestId)?.markFailed(reason)
    }

    fun getByRequestId(requestId: String): CouponIssueRequestModel =
        couponIssueRequestRepository.findByRequestId(requestId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 요청입니다.")
}
