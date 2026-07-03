package com.loopers.interfaces.api.event

import com.loopers.application.event.EventCouponInfo
import com.loopers.application.event.EventCouponStatus
import java.time.LocalDateTime

class EventCouponV1Dto {
    data class DetailResponse(
        val couponId: Long,
        val eventId: Long,
        val eventName: String,
        val startsAt: LocalDateTime,
        val endsAt: LocalDateTime,
        val status: EventCouponStatus,
    ) {
        companion object {
            fun from(info: EventCouponInfo.Detail) = DetailResponse(
                couponId = info.couponId,
                eventId = info.eventId,
                eventName = info.eventName,
                startsAt = info.startsAt,
                endsAt = info.endsAt,
                status = info.status,
            )
        }
    }

    data class RequestResponse(
        val couponId: Long,
        val eventId: Long,
        val idempotencyKey: String?,
        val status: EventCouponStatus,
    ) {
        companion object {
            fun from(info: EventCouponInfo.Request) = RequestResponse(
                couponId = info.couponId,
                eventId = info.eventId,
                idempotencyKey = info.idempotencyKey,
                status = info.status,
            )
        }
    }
}
