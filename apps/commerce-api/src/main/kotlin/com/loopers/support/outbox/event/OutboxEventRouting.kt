package com.loopers.support.outbox.event

import com.loopers.support.outbox.OutboxEventModel

object OutboxEventRouting {
    val publishableTypes: Set<String> = CommerceOutboxEventType.entries.map { it.name }.toSet()

    fun route(event: OutboxEventModel): Route {
        val type = CommerceOutboxEventType.entries.firstOrNull { it.name == event.type }
            ?: throw IllegalArgumentException("Unsupported outbox event type: ${event.type}")
        return Route(
            topicName = type.topicName,
            key = event.aggregateId.toString(),
        )
    }
}
