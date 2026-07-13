package com.loopers.config.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.ZoneId

@ConfigurationProperties(prefix = "commerce.ranking")
data class RankingRedisProperties(
    val zone: String = "Asia/Seoul",
    val viewWeight: Double = 0.05,
    val likeWeight: Double = 0.4,
    val salesWeight: Double = 1.0,
    val projectionChunkSize: Int = 500,
    val carryOver: CarryOver = CarryOver(),
) {
    val zoneId: ZoneId
        get() = ZoneId.of(zone)

    data class CarryOver(
        val topN: Long = 100,
        val factor: Double = 0.1,
        val lockTtlSeconds: Long = 60,
    )
}
