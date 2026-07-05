package com.loopers.application.event

import com.loopers.domain.product.event.ProductEvent
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType

object ProductExternalEventMessagePayload {
    fun from(event: ProductEvent.Viewed): CatalogEventMessage {
        return CatalogEventMessage(
            eventId = event.eventId,
            eventType = CatalogEventType.PRODUCT_VIEWED,
            aggregateId = event.productId,
            productId = event.productId,
            brandId = event.brandId,
            memberId = event.memberId,
            version = event.version,
            occurredAt = event.occurredAt,
        )
    }
}
