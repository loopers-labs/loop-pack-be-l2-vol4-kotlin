package com.loopers.application.coupon.usecase

import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class CouponIssueResult(val requestId: String, val status: CouponIssueStatus, val reason: String?)

@Component
class GetCouponIssueResultUsecase(
    private val requestRepository: CouponIssueRequestRepository,
) {
    @Transactional(readOnly = true)
    fun execute(requestId: String): CouponIssueResult {
        val req = requestRepository.findByRequestId(requestId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다.")
        return CouponIssueResult(req.requestId, req.status, req.reason)
    }
}
