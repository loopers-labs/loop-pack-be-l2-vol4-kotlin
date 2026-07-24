package com.loopers.batch.job.ranking.step

import com.loopers.batch.job.ranking.RankingAggregateJobConfig
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate

@StepScope
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = RankingAggregateJobConfig.JOB_NAME)
@Component("monthlyRankingTasklet")
class MonthlyRankingTasklet(
    private val jdbcTemplate: JdbcTemplate,
    @Value("#{jobParameters['requestDate']}") private val requestDate: LocalDate,
) : Tasklet {
    private val log = LoggerFactory.getLogger(MonthlyRankingTasklet::class.java)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val periodStart = requestDate.withDayOfMonth(1)

        jdbcTemplate.update(
            "DELETE FROM mv_product_rank_monthly WHERE period_start = ?",
            periodStart,
        )

        val inserted = jdbcTemplate.update(
            """
            INSERT INTO mv_product_rank_monthly (product_id, period_start, ranking_score, `rank`)
            SELECT product_id, ?, SUM(ranking_score), 0
            FROM daily_product_ranking_metrics
            WHERE metric_date BETWEEN ? AND ?
            GROUP BY product_id
            """,
            periodStart,
            periodStart,
            requestDate,
        )

        val updated = jdbcTemplate.update(
            """
            UPDATE mv_product_rank_monthly t
            JOIN (
                SELECT product_id,
                       ROW_NUMBER() OVER (ORDER BY ranking_score DESC) AS new_rank
                FROM mv_product_rank_monthly
                WHERE period_start = ?
            ) r ON t.product_id = r.product_id AND t.period_start = ?
            SET t.`rank` = r.new_rank
            """,
            periodStart,
            periodStart,
        )

        log.info("월간 랭킹 집계 완료: period_start=$periodStart, inserted=$inserted, rankUpdated=$updated")
        return RepeatStatus.FINISHED
    }
}
