package com.loopers.batch.job.productrank.step

import com.loopers.batch.job.productrank.AggregationWindow
import com.loopers.batch.job.productrank.RankWeights
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 변형 A2의 집계 단계 — 기간 전체를 GROUP BY **한 번**으로 staging에 적재한다.
 * A1(페이징 GROUP BY)이 페이지마다 집계를 반복하는 비용을 제거하는 대신,
 * 단일 문장이라 chunk 단위 재시작성이 없다 (실패 시 Step 처음부터).
 */
class OneShotAggregateTasklet(
    private val jdbcTemplate: JdbcTemplate,
    private val window: AggregationWindow,
    private val weights: RankWeights,
) : Tasklet {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val inserted = jdbcTemplate.update(
            """
            INSERT INTO product_rank_staging (product_id, score)
            SELECT product_id,
                   SUM(CASE type
                       WHEN '${RankWeights.TYPE_VIEW}' THEN count * ?
                       WHEN '${RankWeights.TYPE_LIKE}' THEN count * ?
                       WHEN '${RankWeights.TYPE_SALES}' THEN count * ?
                       ELSE 0 END)
            FROM product_metrics
            WHERE metric_date BETWEEN ? AND ?
            GROUP BY product_id
            """.trimIndent(),
            weights.viewWeight,
            weights.likeWeight,
            weights.orderWeight,
            window.start,
            window.endInclusive,
        )
        log.info("단발 GROUP BY 집계 완료. window={}~{}, stagingRows={}", window.start, window.endInclusive, inserted)
        return RepeatStatus.FINISHED
    }
}
