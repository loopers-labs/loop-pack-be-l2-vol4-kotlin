package com.loopers.application.ranking

import com.loopers.domain.ranking.ProductRankingBaseline
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScoreCalculator
import com.loopers.infrastructure.metrics.ProductMetricRepository
import com.loopers.infrastructure.ranking.ProductRankingBaselineRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class RankingBaselineScheduler(
    private val productMetricRepository: ProductMetricRepository,
    private val productRankingBaselineRepository: ProductRankingBaselineRepository,
    private val rankingRepository: RankingRepository,
) {
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @Transactional
    fun snapshotBaseline() {
        val today = LocalDate.now()
        val baselines = productMetricRepository.findAll().map {
            ProductRankingBaseline(
                productId = it.id,
                baselineDate = today,
                viewCount = it.viewCount,
                likeCount = it.likeCount,
                salesCount = it.salesCount,
            )
        }
        productRankingBaselineRepository.saveAll(baselines)
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    @Transactional(readOnly = true)
    fun correctRanking() {
        val today = LocalDate.now()
        val baselines = productRankingBaselineRepository.findAll().associateBy { it.productId }
        val metrics = productMetricRepository.findAll()
        rankingRepository.setScores(today, RankingScoreCalculator.dailyScores(metrics, baselines))
    }
}
