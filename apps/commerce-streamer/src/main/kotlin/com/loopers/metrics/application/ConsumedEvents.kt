package com.loopers.metrics.application

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

// commerce-api 가 발행하는 JSON 의 소비자 측 사본 — 필드명·eventType 값이 와이어 계약이다.
interface ConsumedEvent {
    val eventId: String
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "eventType")
@JsonSubTypes(
    JsonSubTypes.Type(ProductEvent.Liked::class, name = "ProductLikedEvent"),
    JsonSubTypes.Type(ProductEvent.Unliked::class, name = "ProductUnlikedEvent"),
)
sealed interface ProductEvent : ConsumedEvent {
    val productId: Long

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Liked(
        override val eventId: String,
        override val productId: Long,
    ) : ProductEvent

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Unliked(
        override val eventId: String,
        override val productId: Long,
    ) : ProductEvent
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderCreatedEvent(
    override val eventId: String,
    val items: List<OrderLine>,
) : ConsumedEvent {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OrderLine(
        val productId: Long,
        val quantity: Long,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProductViewedEvent(
    override val eventId: String,
    val productId: Long,
) : ConsumedEvent
