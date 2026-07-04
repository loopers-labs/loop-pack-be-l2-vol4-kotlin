package com.loopers.domain.coupon.application.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.coupon.model.CouponIssueRequestModel
import com.loopers.domain.coupon.port.CouponTemplateRepository
import com.loopers.domain.coupon.port.CouponIssueRequestRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.outbox.OutboxEventModel
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.outbox.event.CommerceOutboxAggregateType
import com.loopers.support.outbox.event.CommerceOutboxEventType
import java.util.UUID
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CouponIssueRequestService(
    private val couponTemplateRepository: CouponTemplateRepository,
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun requestIssue(
        userId: Long,
        couponTemplateId: Long,
    ): CouponIssueRequestModel {
        couponTemplateRepository.findByIdForUpdateOrNull(couponTemplateId) ?: throw CoreException(ErrorType.NOT_FOUND)
        couponIssueRequestRepository.findByUserIdAndCouponTemplateIdOrNull(userId, couponTemplateId)
            ?.let { return it }

        val request = couponIssueRequestRepository.save(
            CouponIssueRequestModel(
                userId = userId,
                couponTemplateId = couponTemplateId,
            ),
        )
        outboxRepository.save(
            OutboxEventModel(
                type = CommerceOutboxEventType.COUPON_ISSUE_REQUESTED_V1.name,
                aggregateType = CommerceOutboxAggregateType.COUPON_ISSUE_REQUEST.value,
                aggregateId = request.id,
                payload = objectMapper.writeValueAsString(
                    CouponIssueRequestPayload(
                        requestId = request.requestId,
                        userId = userId,
                        couponTemplateId = couponTemplateId,
                    ),
                ),
            ),
        )
        return request
    }

    @Transactional(readOnly = true)
    fun getRequest(userId: Long, requestId: UUID): CouponIssueRequestModel =
        couponIssueRequestRepository.findByRequestIdAndUserIdOrNull(requestId, userId)
            ?: throw CoreException(ErrorType.NOT_FOUND)
}
