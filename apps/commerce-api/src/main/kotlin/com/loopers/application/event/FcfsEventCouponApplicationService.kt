package com.loopers.application.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.coupon.CouponIssueType
import com.loopers.domain.coupon.CouponPublishEventType
import com.loopers.domain.coupon.CouponPublishOutbox
import com.loopers.domain.coupon.CouponPublishOutboxRepository
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.EventCoupon
import com.loopers.domain.coupon.EventCouponRepository
import com.loopers.domain.event.Event
import com.loopers.domain.event.EventRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class FcfsEventCouponApplicationService(
    private val couponRepository: CouponRepository,
    private val eventCouponRepository: EventCouponRepository,
    private val eventRepository: EventRepository,
    private val outboxRepository: CouponPublishOutboxRepository,
    private val uuidV7Generator: UuidV7Generator,
    private val objectMapper: ObjectMapper,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional(readOnly = true)
    fun get(userId: Long, couponId: Long, now: LocalDateTime = LocalDateTime.now()): EventCouponInfo.Detail {
        val eventCoupon = getEventCoupon(couponId)
        val event = getEvent(eventCoupon.eventId)
        val status = resolveDisplayStatus(userId = userId, eventCoupon = eventCoupon, event = event, now = now)
        return EventCouponInfo.Detail.from(eventCoupon, event, status)
    }

    @Transactional
    fun request(userId: Long, couponId: Long, now: LocalDateTime = LocalDateTime.now()): EventCouponInfo.Request {
        val eventCoupon = getEventCoupon(couponId)
        val event = getEvent(eventCoupon.eventId)

        if (!event.isActive(now)) {
            return EventCouponInfo.Request.terminal(couponId = eventCoupon.id, eventId = event.id, status = EventCouponStatus.EVENT_ENDED)
        }
        if (hasSuccessfulRequest(userId = userId, couponId = eventCoupon.id)) {
            return EventCouponInfo.Request.terminal(couponId = eventCoupon.id, eventId = event.id, status = EventCouponStatus.ALREADY_REGISTERED)
        }
        if (!eventCouponRepository.reserveOneIfAvailable(eventCoupon.id)) {
            return EventCouponInfo.Request.terminal(couponId = eventCoupon.id, eventId = event.id, status = EventCouponStatus.EVENT_ENDED)
        }

        val idempotencyKey = uuidV7Generator.generate().toString()
        val message = CouponPublishRequestedMessage(
            idempotencyKey = idempotencyKey,
            eventId = event.id,
            couponId = eventCoupon.id,
            userId = userId,
        )
        val outbox = outboxRepository.saveAndFlush(
            CouponPublishOutbox(
                idempotencyKey = idempotencyKey,
                eventType = message.eventType,
                eventId = event.id,
                couponId = eventCoupon.id,
                userId = userId,
                payload = objectMapper.writeValueAsString(message),
            ),
        )
        applicationEventPublisher.publishEvent(CouponPublishRequestedApplicationEvent(outboxId = outbox.id, message = message))

        return EventCouponInfo.Request.requested(couponId = eventCoupon.id, eventId = event.id, idempotencyKey = idempotencyKey)
    }

    private fun resolveDisplayStatus(userId: Long, eventCoupon: EventCoupon, event: Event, now: LocalDateTime): EventCouponStatus =
        when {
            hasSuccessfulRequest(userId = userId, couponId = eventCoupon.id) -> EventCouponStatus.ALREADY_REGISTERED
            !event.isActive(now) || eventCoupon.isExhausted() -> EventCouponStatus.EVENT_ENDED
            else -> EventCouponStatus.AVAILABLE
        }

    private fun getEventCoupon(couponId: Long): EventCoupon {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        if (coupon.getIssueType() != CouponIssueType.FIRST_COME_FIRST_SERVED) {
            throw CoreException(ErrorType.CONFLICT, "선착순 이벤트 쿠폰이 아닙니다.")
        }
        return eventCouponRepository.findByCouponId(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "이벤트 쿠폰을 찾을 수 없습니다.")
    }

    private fun getEvent(eventId: Long): Event =
        eventRepository.findById(eventId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "이벤트를 찾을 수 없습니다.")

    private fun hasSuccessfulRequest(userId: Long, couponId: Long): Boolean =
        outboxRepository.existsSuccessfulRequest(
            eventType = CouponPublishEventType.COUPON_PUBLISH_REQUESTED,
            couponId = couponId,
            userId = userId,
        )
}
