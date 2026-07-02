package com.loopers.support.outbox.relay

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "commerce-events.like-count.relay",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class OutboxRelayScheduler(
    private val outboxRelay: OutboxRelay,
) {
    @Scheduled(fixedDelayString = "\${commerce-events.like-count.relay.fixed-delay-ms:1000}")
    fun publishLikeCountEvents() {
        outboxRelay.publishOnce()
    }
}
