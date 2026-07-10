package com.loopers.application.coupon

import com.loopers.application.event.CouponIssueRequestedEvent
import com.loopers.domain.coupon.CouponIssueResult
import com.loopers.domain.coupon.CouponIssueResultRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class CouponIssueFacade(
    private val couponRepository: CouponRepository,
    private val couponIssueResultRepository: CouponIssueResultRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun requestIssue(userId: Long, couponId: Long): String {
        couponRepository.findById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다. id=$couponId")

        val requestId = UUID.randomUUID().toString()

        couponIssueResultRepository.save(
            CouponIssueResult.pending(
                requestId = requestId,
                userId = userId,
                couponId = couponId,
            ),
        )

        eventPublisher.publishEvent(
            CouponIssueRequestedEvent(
                userId = userId,
                couponId = couponId,
                requestId = requestId,
            ),
        )

        return requestId
    }

    @Transactional(readOnly = true)
    fun getIssueResult(requestId: String): CouponIssueResultInfo {
        val result = couponIssueResultRepository.findByRequestId(requestId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다. requestId=$requestId")
        return CouponIssueResultInfo(
            requestId = result.requestId,
            status = result.status,
            reason = result.reason,
        )
    }
}

data class CouponIssueResultInfo(
    val requestId: String,
    val status: CouponIssueStatus,
    val reason: String?,
)
