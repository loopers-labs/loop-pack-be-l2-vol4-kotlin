package com.loopers.support.outbox.relay

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "commerce-events.outbox-relay")
data class OutboxRelayProperties(
    val relayBatchSize: Int = 50,
    val relayRetryDelay: Duration = Duration.ofMinutes(1),
    val relayClaimLease: Duration = Duration.ofMinutes(5),
    val relayPublishTimeout: Duration = Duration.ofSeconds(5),
    val maxPublishAttempts: Int = 5,
) {
    init {
        require(maxPublishAttempts > 0) { "maxPublishAttempts must be greater than zero." }
    }
}
