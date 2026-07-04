package com.loopers.domain.coupon

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.coupon.CouponIssueRequestPayload
import com.loopers.config.kafka.KafkaTopics
import com.loopers.domain.outbox.OutboxModel
import com.loopers.domain.outbox.OutboxRepository
import com.loopers.support.function.orThrowNotFound
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

@Component
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper,
) {
    fun createCoupon(name: String, type: CouponType, value: Double, minOrderAmount: Double, expiredAt: ZonedDateTime, quantity: Int): CouponModel =
        CouponModel.of(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
            quantity = quantity,
        ).let { couponRepository.save(it) }

    fun getCoupon(couponId: Long): CouponModel =
        couponRepository.findByIdOrNull(couponId) orThrowNotFound "해당하는 쿠폰이 존재하지 않습니다."

    fun getCoupons(pageable: Pageable): Page<CouponModel> =
        couponRepository.findAll(pageable)

    fun createCouponIssueRequest(requestId: String, couponId: Long, userId: Long) {
        couponIssueRequestRepository.save(CouponIssueRequestModel.of(requestId = requestId, couponId = couponId, userId = userId))
        outboxRepository.save(OutboxModel.of(
            aggregateId = couponId.toString(),
            eventType = "coupon_issued",
            topic = KafkaTopics.COUPON_ISSUE_REQUESTS,
            payload = objectMapper.writeValueAsString(
                CouponIssueRequestPayload(requestId = requestId, couponId = couponId, userId = userId)
            )
        ))
    }
}
