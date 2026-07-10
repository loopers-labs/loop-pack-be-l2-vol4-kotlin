package com.loopers.application.event

import java.time.ZonedDateTime

data class ProductLikeMetricIncreasedEvent(
    val userId: Long,
    val productId: Long,
    override val occurredAt: String = ZonedDateTime.now().toString(),
) : IntegrationEvent {
    override val eventType: String = EVENT_TYPE
    override val aggregateType: String = EventAggregateType.PRODUCT.value
    override val aggregateId: String = productId.toString()
    override val topic: String = EventTopic.CATALOG_EVENTS.value

    companion object {
        const val EVENT_TYPE = "ProductLikeMetricIncreased"
    }
}

data class ProductLikeMetricDecreasedEvent(
    val userId: Long,
    val productId: Long,
    override val occurredAt: String = ZonedDateTime.now().toString(),
) : IntegrationEvent {
    override val eventType: String = EVENT_TYPE
    override val aggregateType: String = EventAggregateType.PRODUCT.value
    override val aggregateId: String = productId.toString()
    override val topic: String = EventTopic.CATALOG_EVENTS.value

    companion object {
        const val EVENT_TYPE = "ProductLikeMetricDecreased"
    }
}
