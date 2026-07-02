package com.loopers.infrastructure.event

import com.loopers.application.event.EventOutboxRelayService
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class EventOutboxRelayScheduler(
    private val eventOutboxRelayService: EventOutboxRelayService,
    @Value("\${commerce.outbox.relay.batch-size:100}")
    private val batchSize: Int,
) {
    @Scheduled(fixedDelayString = "\${commerce.outbox.relay.fixed-delay-millis:1000}")
    fun relay() {
        eventOutboxRelayService.relayPending(batchSize)
    }
}
