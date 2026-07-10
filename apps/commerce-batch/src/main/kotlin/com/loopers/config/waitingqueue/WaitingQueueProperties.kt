package com.loopers.config.waitingqueue

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "waiting-queue.orders")
class WaitingQueueProperties {
    var enabled: Boolean = true
    var consumeDelay: Duration = Duration.ofMillis(100)
    var admitCount: Long = 5
    var allowedTtl: Duration = Duration.ofSeconds(60)
    var heartbeatTtl: Duration = Duration.ofSeconds(10)
}
