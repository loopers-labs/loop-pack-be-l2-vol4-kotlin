package com.loopers.infrastructure.like

import com.loopers.domain.like.event.LikeEventPublisher
import com.loopers.domain.like.event.ProductLikeEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class LikeCoreEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : LikeEventPublisher {
    override fun publish(event: ProductLikeEvent.Like) {
        applicationEventPublisher.publishEvent(event)
    }

    override fun publish(event: ProductLikeEvent.Unlike) {
        applicationEventPublisher.publishEvent(event)
    }
}
