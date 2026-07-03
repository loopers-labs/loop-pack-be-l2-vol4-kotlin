package com.loopers.domain.coupon.application.service

import com.loopers.domain.coupon.exception.CouponNotIssuableException
import com.loopers.domain.coupon.exception.DuplicateIssuedCouponException
import com.loopers.domain.coupon.model.CouponIssueRequestStatus
import com.loopers.domain.coupon.port.CouponIssueEventHandledRepository
import com.loopers.domain.coupon.port.CouponIssueRequestRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CouponIssueRequestWorker(
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val couponIssueEventHandledRepository: CouponIssueEventHandledRepository,
    private val couponService: CouponService,
) {
    @Transactional
    fun process(
        eventId: UUID,
        consumerGroup: String,
        eventType: String,
        requestId: UUID,
    ): CouponIssueRequestStatus {
        if (!couponIssueEventHandledRepository.recordIfAbsent(eventId, consumerGroup, eventType)) {
            return couponIssueRequestRepository.findByRequestIdOrNull(requestId)?.status
                ?: CouponIssueRequestStatus.PENDING
        }

        val request = couponIssueRequestRepository.findByRequestIdForUpdateOrNull(requestId)
            ?: throw CoreException(ErrorType.NOT_FOUND)
        if (request.status != CouponIssueRequestStatus.PENDING) {
            return request.status
        }

        val updated = try {
            val issuedCoupon = couponService.issue(request.userId, request.couponTemplateId)
            request.markIssued(issuedCoupon.id)
        } catch (_: DuplicateIssuedCouponException) {
            request.markDuplicate()
        } catch (e: CouponNotIssuableException) {
            request.markSoldOut(e.message)
        }
        return couponIssueRequestRepository.save(updated).status
    }
}
