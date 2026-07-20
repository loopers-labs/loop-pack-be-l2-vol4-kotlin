package com.loopers.config.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.ZoneId

@ConfigurationProperties(prefix = "commerce.ranking")
data class RankingRedisProperties(
    val zone: String = "Asia/Seoul",
    val viewWeight: Double = 0.05,
    val likeWeight: Double = 0.4,
    val salesWeight: Double = 1.0,
    val eventChunkSize: Int = 500,
    val carryOver: CarryOver = CarryOver(),
) {
    init {
        require(viewWeight.isFinite() && viewWeight >= 0.0) { "Ranking view weight must be a non-negative finite number." }
        require(likeWeight.isFinite() && likeWeight >= 0.0) { "Ranking like weight must be a non-negative finite number." }
        require(salesWeight.isFinite() && salesWeight >= 0.0) { "Ranking sales weight must be a non-negative finite number." }
        require(eventChunkSize > 0) { "Ranking update chunk size must be positive." }
        require(carryOver.topN > 0) { "Ranking carry-over topN must be positive." }
        require(carryOver.factor.isFinite() && carryOver.factor >= 0.0) {
            "Ranking carry-over factor must be a non-negative finite number."
        }
        require(carryOver.lockTtlSeconds > 0) { "Ranking carry-over lock TTL must be positive." }
    }

    val zoneId: ZoneId
        get() = ZoneId.of(zone)

    data class CarryOver(
        val topN: Long = 100,
        val factor: Double = 0.1,
        val lockTtlSeconds: Long = 60,
        val cron: String = "0 50 23 * * *",
        val zone: String = "Asia/Seoul",
    )
}
