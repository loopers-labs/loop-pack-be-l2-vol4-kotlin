package com.loopers.coupon.application

import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponIssueResult
import com.loopers.coupon.domain.CouponIssueResultRepository
import com.loopers.coupon.domain.CouponIssueResultStatus
import com.loopers.coupon.infrastructure.messaging.CouponIssueRequestEvent
import com.loopers.support.error.NotFoundException
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponIssueProcessor(
    val couponService: CouponService,
    val couponIssueResultRepository: CouponIssueResultRepository,
) {
    @Transactional
    fun register(couponIssueRequestEvent: CouponIssueRequestEvent): Boolean {
        val existing = couponIssueResultRepository.findById(couponIssueRequestEvent.requestId)
        if (existing != null) {
            return existing.status == CouponIssueResultStatus.PENDING
        }
        couponIssueResultRepository.save(
            CouponIssueResult(
                requestId = couponIssueRequestEvent.requestId,
                couponId = couponIssueRequestEvent.couponId,
                userId = couponIssueRequestEvent.userId,
                requestedAt = couponIssueRequestEvent.requestedAt,
            ),
        )
        return true
    }

    @Transactional
    fun approve(couponIssueRequestEvent: CouponIssueRequestEvent) {
        val couponIssueResult = findResult(couponIssueRequestEvent.requestId)
        val couponIssueInfo = couponService.issue(
            CouponIssueCommand(couponIssueRequestEvent.couponId, couponIssueRequestEvent.userId),
        )
        couponIssueResult.markIssued(couponIssueInfo.userCouponId, LocalDateTime.now())
    }

    @Transactional
    fun reject(requestId: String, rejectReason: String) {
        findResult(requestId).markRejected(rejectReason, LocalDateTime.now())
    }

    private fun findResult(requestId: String): CouponIssueResult =
        couponIssueResultRepository.findById(requestId)
            ?: throw NotFoundException(CouponErrorCode.ISSUE_REQUEST_NOT_FOUND)
}
