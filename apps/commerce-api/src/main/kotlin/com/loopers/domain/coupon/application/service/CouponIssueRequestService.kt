package com.loopers.domain.coupon.application.service

import com.loopers.domain.coupon.model.CouponIssueRequestModel
import com.loopers.domain.coupon.port.CouponIssueRequestRepository
import com.loopers.domain.coupon.port.CouponTemplateRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.event.CouponIssueRequestedApplicationEvent
import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Component
class CouponIssueRequestService(
    private val couponTemplateRepository: CouponTemplateRepository,
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val applicationEventPublisher: ApplicationEventPublisher = ApplicationEventPublisher { },
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun requestIssue(
        userId: Long,
        couponTemplateId: Long,
    ): CouponIssueRequestModel {
        couponTemplateRepository.findByIdOrNull(couponTemplateId) ?: throw CoreException(ErrorType.NOT_FOUND)
        couponIssueRequestRepository.findByUserIdAndCouponTemplateIdOrNull(userId, couponTemplateId)
            ?.let { return it }

        val candidate = CouponIssueRequestModel(
            userId = userId,
            couponTemplateId = couponTemplateId,
        )
        val inserted = couponIssueRequestRepository.insertIfAbsent(candidate)
        val request = couponIssueRequestRepository.findByUserIdAndCouponTemplateIdOrNull(userId, couponTemplateId)
            ?: throw DataIntegrityViolationException(
                "Coupon issue request insert conflicted outside the user-template uniqueness boundary.",
            )
        if (inserted == 0) {
            return request
        }
        applicationEventPublisher.publishEvent(
            CouponIssueRequestedApplicationEvent(
                requestId = request.requestId,
                requestAggregateId = request.id,
                userId = userId,
                couponTemplateId = couponTemplateId,
            ),
        )
        return request
    }

    @Transactional(readOnly = true)
    fun getRequest(userId: Long, requestId: UUID): CouponIssueRequestModel =
        couponIssueRequestRepository.findByRequestIdAndUserIdOrNull(requestId, userId)
            ?: throw CoreException(ErrorType.NOT_FOUND)
}
