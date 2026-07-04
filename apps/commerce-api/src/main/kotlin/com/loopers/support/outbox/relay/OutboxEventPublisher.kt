package com.loopers.support.outbox.relay

import com.loopers.support.outbox.OutboxEventModel

interface OutboxEventPublisher {
    fun publish(event: OutboxEventModel)
}
