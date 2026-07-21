package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.AggregationPeriod
import com.loopers.batch.job.ranking.MonthlyRankingJobConfig
import com.loopers.batch.job.ranking.ProductMetricsAggregate
import com.loopers.batch.job.ranking.RankingScoreWeights
import com.loopers.batch.metrics.ProductMetricsMonthly
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZonedDateTime

@Component
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = MonthlyRankingJobConfig.JOB_NAME)
class MonthlyRankingProcessor(
    @Value("#{jobParameters['baseDate']}") baseDate: String,
) : ItemProcessor<ProductMetricsAggregate, ProductMetricsMonthly> {
    private val yearMonth = AggregationPeriod.monthlyOf(LocalDate.parse(baseDate)).key

    override fun process(item: ProductMetricsAggregate): ProductMetricsMonthly =
        ProductMetricsMonthly(
            productId = item.productId,
            yearMonth = yearMonth,
            likeCount = item.likeCount,
            salesCount = item.salesCount,
            viewCount = item.viewCount,
            score = RankingScoreWeights.score(item.likeCount, item.salesCount, item.viewCount),
            updatedAt = ZonedDateTime.now(),
        )
}