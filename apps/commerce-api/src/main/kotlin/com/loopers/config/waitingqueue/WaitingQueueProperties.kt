package com.loopers.config.waitingqueue

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "waiting-queue.orders")
class WaitingQueueProperties {
    var enabled: Boolean = true
    var trafficThresholdPerSecond: Long = 100
    var tokenTtl: Duration = Duration.ofMinutes(30)
    var allowedTtl: Duration = Duration.ofSeconds(60)
    var heartbeatTtl: Duration = Duration.ofSeconds(10)
    var admitRatePerSecond: Long = 50
}
