package com.loopers.application.queue

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "commerce.queue")
data class WaitingQueueProperties(
    val estimatedThroughputPerSecond: Long = 50,
    val entryTokenTtlSeconds: Long = 300,
    val scheduler: Scheduler = Scheduler(),
) {
    val entryTokenTtl: Duration
        get() = Duration.ofSeconds(entryTokenTtlSeconds)

    data class Scheduler(
        val batchSize: Long = 50,
    )
}
