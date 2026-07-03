package com.loopers.application.event

import com.loopers.domain.coupon.EventCoupon
import com.loopers.domain.event.Event
import java.time.LocalDateTime

class EventCouponInfo {
    data class Detail(
        val couponId: Long,
        val eventId: Long,
        val eventName: String,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime,
        val status: EventCouponStatus,
    ) {
        companion object {
            fun from(eventCoupon: EventCoupon, event: Event, status: EventCouponStatus) = Detail(
                couponId = eventCoupon.id,
                eventId = event.id,
                eventName = event.name,
                startsAt = event.startsAt,
                endsAt = event.endsAt,
                status = status,
            )
        }
    }

    data class Request(
        val couponId: Long,
        val eventId: Long,
        val idempotencyKey: String?,
        val status: EventCouponStatus,
    ) {
        companion object {
            fun requested(couponId: Long, eventId: Long, idempotencyKey: String) = Request(
                couponId = couponId,
                eventId = eventId,
                idempotencyKey = idempotencyKey,
                status = EventCouponStatus.REQUESTED,
            )

            fun terminal(couponId: Long, eventId: Long, status: EventCouponStatus) = Request(
                couponId = couponId,
                eventId = eventId,
                idempotencyKey = null,
                status = status,
            )
        }
    }
}
