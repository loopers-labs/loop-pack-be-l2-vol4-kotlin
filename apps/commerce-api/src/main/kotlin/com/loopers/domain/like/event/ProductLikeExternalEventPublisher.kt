package com.loopers.domain.like.event

import com.loopers.event.CatalogEventMessage

interface ProductLikeExternalEventPublisher {
    fun publish(
        topic: String,
        partitionKey: String,
        message: CatalogEventMessage,
    )
}
