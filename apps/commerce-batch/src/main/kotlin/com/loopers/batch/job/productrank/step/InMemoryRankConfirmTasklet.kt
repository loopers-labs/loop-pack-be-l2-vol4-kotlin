package com.loopers.batch.job.productrank.step

import com.loopers.batch.job.productrank.RankPeriod
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate

/**
 * 변형 B2의 확정 단계 — staging 대신 인메모리 집계 결과에서 TOP 100을 뽑아 MV에 반영한다.
 * DELETE + INSERT가 Step 트랜잭션 안에서 원자적으로 교체되는 것은 RankConfirmTasklet과 동일.
 */
class InMemoryRankConfirmTasklet(
    private val jdbcTemplate: JdbcTemplate,
    private val period: RankPeriod,
    private val aggregatedDate: LocalDate,
    private val accumulator: ProductScoreAccumulator,
) : Tasklet {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val top = accumulator.top(RankConfirmTasklet.TOP_N)
        val deleted = jdbcTemplate.update("DELETE FROM ${period.mvTable} WHERE aggregated_date = ?", aggregatedDate)
        jdbcTemplate.batchUpdate(
            """
            INSERT INTO ${period.mvTable} (rank_no, product_id, score, aggregated_date, created_at, updated_at)
            VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            """.trimIndent(),
            top.mapIndexed { index, score -> arrayOf<Any>(index + 1, score.productId, score.score, aggregatedDate) },
        )
        log.info(
            "기간 랭킹 확정(인메모리). table={}, aggregatedDate={}, accumulated={}, replaced={}, inserted={}",
            period.mvTable,
            aggregatedDate,
            accumulator.size(),
            deleted,
            top.size,
        )
        return RepeatStatus.FINISHED
    }
}
