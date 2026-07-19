package com.loopers.application.ranking

import com.loopers.config.redis.RankingClockConfig
import com.loopers.config.redis.RankingRedisProperties
import com.loopers.domain.ranking.RankingPage
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingUnavailableException
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class RankingQueryService(
    private val rankingRepository: RankingRepository,
    meterRegistry: MeterRegistry,
    private val properties: RankingRedisProperties,
    @Qualifier(RankingClockConfig.RANKING_CLOCK)
    private val clock: Clock,
) {
    private val degradedDetailCounter = meterRegistry.counter("ranking.product.detail.degraded")

    fun getPage(date: LocalDate, page: Int, size: Int): RankingPage {
        return rankingRepository.findPage(date = date, page = page, size = size)
    }

    fun getRankOrNull(date: LocalDate, productId: Long): Long? {
        return try {
            rankingRepository.findRank(date = date, productId = productId)
        } catch (e: RankingUnavailableException) {
            degradedDetailCounter.increment()
            log.warn("Failed to read product rank. productId={}, date={}", productId, date, e)
            null
        }
    }

    fun getTodayRankOrNull(productId: Long): Long? {
        val today = LocalDate.now(clock.withZone(properties.zoneId))
        return getRankOrNull(date = today, productId = productId)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(RankingQueryService::class.java)
    }
}
