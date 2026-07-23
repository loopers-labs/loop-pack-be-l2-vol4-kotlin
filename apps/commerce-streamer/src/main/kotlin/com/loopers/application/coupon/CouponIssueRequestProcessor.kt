package com.loopers.application.coupon

import com.loopers.domain.coupon.enums.CouponIssueRequestStatus
import com.loopers.domain.coupon.model.CouponIssue
import com.loopers.domain.coupon.repository.CouponIssueRepository
import com.loopers.domain.coupon.repository.CouponIssueRequestRepository
import com.loopers.domain.coupon.repository.CouponRepository
import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.event.CouponIssueRequestMessage
import com.loopers.event.NonRetryableEventException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponIssueRequestProcessor(
    private val couponRepository: CouponRepository,
    private val couponIssueRepository: CouponIssueRepository,
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val eventHandledRepository: EventHandledRepository,
    @Value("\${commerce.coupon.consumer-group:commerce-coupon-issue}")
    private val consumerGroup: String = "commerce-coupon-issue",
) {
    @Transactional
    fun handle(message: CouponIssueRequestMessage) {
        if (eventHandledRepository.exists(consumerGroup, message.eventId)) {
            return
        }

        val request = couponIssueRequestRepository.findByRequestIdForUpdate(message.requestId)
            ?: throw NonRetryableEventException("Coupon issue request not found.")

        if (request.status != CouponIssueRequestStatus.REQUESTED) {
            recordHandled(message)
            return
        }

        val coupon = couponRepository.findByIdForUpdate(message.couponId)
        if (coupon == null || coupon.isDeleted) {
            request.reject("Coupon not found.")
            couponIssueRequestRepository.save(request)
            recordHandled(message)
            return
        }

        if (!coupon.isValid()) {
            request.reject("Coupon is not valid.")
            couponIssueRequestRepository.save(request)
            recordHandled(message)
            return
        }

        if (couponIssueRepository.existsByCouponIdAndMemberId(couponId = coupon.id, memberId = message.memberId)) {
            request.reject("Coupon already issued.")
            couponIssueRequestRepository.save(request)
            recordHandled(message)
            return
        }

        if (!coupon.hasRemainingIssueQuantity()) {
            request.reject("Coupon issue limit exceeded.")
            couponIssueRequestRepository.save(request)
            recordHandled(message)
            return
        }

        coupon.increaseIssuedCount()
        couponRepository.update(coupon)
        val issue = CouponIssue.issue(memberId = message.memberId, coupon = coupon)
            .let(couponIssueRepository::save)
        request.issue(issue.id)
        couponIssueRequestRepository.save(request)
        recordHandled(message)
    }

    private fun recordHandled(message: CouponIssueRequestMessage) {
        eventHandledRepository.save(
            EventHandled(
                consumerGroup = consumerGroup,
                eventId = message.eventId,
                eventType = "COUPON_ISSUE_REQUESTED",
            ),
        )
    }
}
