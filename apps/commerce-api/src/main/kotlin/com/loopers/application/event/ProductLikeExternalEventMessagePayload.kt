package com.loopers.application.event

import com.loopers.domain.like.event.ProductLikeEvent
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType

object ProductLikeExternalEventMessagePayload {
    fun from(event: ProductLikeEvent.Like): CatalogEventMessage {
        return CatalogEventMessage(
            eventId = event.eventId,
            eventType = CatalogEventType.PRODUCT_LIKED,
            aggregateId = event.productId,
            productId = event.productId,
            brandId = event.brandId,
            memberId = event.memberId,
            version = event.version,
            occurredAt = event.occurredAt,
        )
    }

    fun from(event: ProductLikeEvent.Unlike): CatalogEventMessage {
        return CatalogEventMessage(
            eventId = event.eventId,
            eventType = CatalogEventType.PRODUCT_UNLIKED,
            aggregateId = event.productId,
            productId = event.productId,
            brandId = event.brandId,
            memberId = event.memberId,
            version = event.version,
            occurredAt = event.occurredAt,
        )
    }
}
