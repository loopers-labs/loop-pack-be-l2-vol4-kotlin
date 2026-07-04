package com.loopers.application.coupon

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.kafka.KafkaTopics
import com.loopers.domain.coupon.CouponIssueRequestModel
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.UserCouponService
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.outbox.OutboxModel
import com.loopers.domain.outbox.OutboxRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime
import java.util.UUID

@Component
class CouponFacade(
    private val couponService: CouponService,
    private val userCouponService: UserCouponService,
    private val userService: UserService,
    private val eventHandledRepository: EventHandledRepository,
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    fun issueCoupon(loginId: String, couponId: Long): MyCouponInfo {
        val user = userService.getByLoginId(loginId)
        val coupon = couponService.getCoupon(couponId)
        val userCoupon = userCouponService.issueCoupon(user.id, coupon)
        val now = ZonedDateTime.now()

        return MyCouponInfo.from(userCoupon, now)
    }

    fun getCoupons(loginId: String): List<MyCouponInfo> {
        val user = userService.getByLoginId(loginId)
        val now = ZonedDateTime.now()
        return userCouponService.getByUserId(user.id)
            .map { MyCouponInfo.from(it, now) }
    }

    @Transactional(readOnly = true)
    fun getIssueRequest(requestId: String): CouponIssueRequestInfo {
        val request = couponIssueRequestRepository.findByRequestId(requestId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "requestId에 해당하는 쿠폰 생성 요청이 없습니다.")
        return CouponIssueRequestInfo.from(request)
    }

    @Transactional
    fun requestIssue(loginId: String, couponId: Long): String {
        val user = userService.getByLoginId(loginId)
        val coupon = couponService.getCoupon(couponId)

        val requestId = UUID.randomUUID().toString()

        couponService.createCouponIssueRequest(requestId, coupon.id, user.id)

        couponIssueRequestRepository.save(CouponIssueRequestModel.of(requestId = requestId, couponId = coupon.id, userId = user.id))
        outboxRepository.save(OutboxModel.of(
            aggregateId = coupon.id.toString(),
            eventType = "coupon_issued",
            topic = KafkaTopics.COUPON_ISSUE_REQUESTS,
            payload = objectMapper.writeValueAsString(
                CouponIssueRequestPayload(requestId = requestId, couponId = coupon.id, userId = user.id)
            )
        ))

        return requestId
    }

    @Transactional
    fun issue(payload: CouponIssueRequestPayload) {
        if (eventHandledRepository.insertIfAbsent(payload.requestId, ZonedDateTime.now()) == 0) return
        val couponIssueRequest = couponIssueRequestRepository.findByRequestId(payload.requestId) ?: return
        val coupon = couponService.getCoupon(payload.couponId)
        if (userCouponService.countIssuedByCouponId(coupon.id) >= coupon.quantity) {
            couponIssueRequest.markSoldOut()
            return
        }
        if (userCouponService.hasIssuedTo(coupon.id, payload.userId)) {
            couponIssueRequest.markFailed()
            return
        }
        userCouponService.issueCoupon(payload.userId, coupon)
        couponIssueRequest.markSuccess()
    }
}

data class CouponIssueRequestPayload(
    val requestId: String,
    val couponId: Long,
    val userId: Long,
)
