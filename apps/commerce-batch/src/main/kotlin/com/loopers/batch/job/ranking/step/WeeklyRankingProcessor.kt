package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.AggregationPeriod
import com.loopers.batch.job.ranking.ProductMetricsAggregate
import com.loopers.batch.job.ranking.RankingScoreWeights
import com.loopers.batch.job.ranking.WeeklyRankingJobConfig
import com.loopers.batch.metrics.ProductMetricsWeekly
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZonedDateTime

@Component
@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingProcessor(
    @Value("#{jobParameters['baseDate']}") baseDate: String,
) : ItemProcessor<ProductMetricsAggregate, ProductMetricsWeekly> {
    private val yearWeek = AggregationPeriod.weeklyOf(LocalDate.parse(baseDate)).key

    override fun process(item: ProductMetricsAggregate): ProductMetricsWeekly =
        ProductMetricsWeekly(
            productId = item.productId,
            yearWeek = yearWeek,
            likeCount = item.likeCount,
            salesCount = item.salesCount,
            viewCount = item.viewCount,
            score = RankingScoreWeights.score(item.likeCount, item.salesCount, item.viewCount),
            updatedAt = ZonedDateTime.now(),
        )
}
