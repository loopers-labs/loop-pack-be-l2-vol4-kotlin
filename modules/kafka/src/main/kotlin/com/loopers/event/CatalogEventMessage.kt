package com.loopers.event

import java.time.ZonedDateTime

data class CatalogEventMessage(
    val eventId: String,
    val eventType: CatalogEventType,
    val aggregateId: Long,
    val productId: Long,
    val brandId: Long,
    val memberId: Long?,
    val version: Long,
    val occurredAt: ZonedDateTime,
)

enum class CatalogEventType {
    PRODUCT_LIKED,
    PRODUCT_UNLIKED,
    PRODUCT_VIEWED,
}
