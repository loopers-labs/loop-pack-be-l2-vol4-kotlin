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
 * staging의 상품별 점수에서 TOP 100을 뽑아 rank를 부여하고 MV에 반영한다.
 * 같은 aggregated_date의 기존 행 DELETE + INSERT가 한 트랜잭션(Step 경계)이라 교체가 원자적이다 —
 * 재실행해도 스냅샷이 통째로 갈아끼워질 뿐 중복되지 않는다.
 */
class RankConfirmTasklet(
    private val jdbcTemplate: JdbcTemplate,
    private val period: RankPeriod,
    private val aggregatedDate: LocalDate,
) : Tasklet {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        val deleted = jdbcTemplate.update("DELETE FROM ${period.mvTable} WHERE aggregated_date = ?", aggregatedDate)
        val inserted = jdbcTemplate.update(
            """
            INSERT INTO ${period.mvTable} (rank_no, product_id, score, aggregated_date, created_at, updated_at)
            SELECT ROW_NUMBER() OVER (ORDER BY score DESC, product_id ASC), product_id, score, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
            FROM product_rank_staging
            ORDER BY score DESC, product_id ASC
            LIMIT $TOP_N
            """.trimIndent(),
            aggregatedDate,
        )
        log.info(
            "기간 랭킹 확정. table={}, aggregatedDate={}, replaced={}, inserted={}",
            period.mvTable,
            aggregatedDate,
            deleted,
            inserted,
        )
        return RepeatStatus.FINISHED
    }

    companion object {
        const val TOP_N = 100
    }
}
