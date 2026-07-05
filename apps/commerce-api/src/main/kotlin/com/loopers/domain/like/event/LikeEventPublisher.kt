package com.loopers.domain.like.event

interface LikeEventPublisher {
    fun publish(event: ProductLikeEvent.Like)
    fun publish(event: ProductLikeEvent.Unlike)
}
