package com.loopers.infrastructure.outbox

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// ponytail: 테스트 결정성을 위해 relayOnce 실행 로직과 분리. outbox.relay.scheduler.enabled=false로 테스트에서 끈다.
@Component
@ConditionalOnProperty(name = ["outbox.relay.scheduler.enabled"], havingValue = "true", matchIfMissing = true)
class OutboxRelayScheduler(
    private val outboxRelay: OutboxRelay,
) {
    private val log = LoggerFactory.getLogger(OutboxRelayScheduler::class.java)

    @Scheduled(fixedDelay = 1000)
    fun relay() {
        runCatching { outboxRelay.relayOnce() }
            .onFailure { log.warn("Outbox relay failed", it) }
    }
}
