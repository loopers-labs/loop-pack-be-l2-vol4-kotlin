package com.loopers.support.outbox.relay

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "commerce-events.like-count")
data class LikeCountOutboxRelayProperties(
    val topicName: String = DEFAULT_TOPIC_NAME,
    val relayBatchSize: Int = 50,
    val relayRetryDelay: Duration = Duration.ofMinutes(1),
    val relayPublishTimeout: Duration = Duration.ofSeconds(5),
) {
    companion object {
        const val EVENT_TYPE = "LIKE_COUNT_CHANGED_V1"
        const val DEFAULT_TOPIC_NAME = "commerce.like-count-events.v1"
    }
}
