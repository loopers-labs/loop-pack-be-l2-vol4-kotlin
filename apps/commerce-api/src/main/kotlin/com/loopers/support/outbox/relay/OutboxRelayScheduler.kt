package com.loopers.support.outbox.relay

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Configuration
@EnableScheduling
class OutboxRelaySchedulingConfig

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
