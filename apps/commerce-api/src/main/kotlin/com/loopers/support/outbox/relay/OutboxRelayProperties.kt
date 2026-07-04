package com.loopers.support.outbox.relay

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "commerce-events.outbox-relay")
data class OutboxRelayProperties(
    val relayBatchSize: Int = 50,
    val relayRetryDelay: Duration = Duration.ofMinutes(1),
    val relayPublishTimeout: Duration = Duration.ofSeconds(5),
)
