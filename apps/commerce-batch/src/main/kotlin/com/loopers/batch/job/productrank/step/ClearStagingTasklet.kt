package com.loopers.batch.job.productrank.step

import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 집계 Step 진입 전 staging을 비운다. 같은 파라미터로 재실행해도 이전 실행의 잔여/부분 결과가
 * 섞이지 않게 하는 멱등성 장치다. TRUNCATE는 DDL(암묵 커밋)이라 트랜잭션 밖 전용 Step으로 분리했다.
 */
class ClearStagingTasklet(
    private val jdbcTemplate: JdbcTemplate,
) : Tasklet {
    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus {
        jdbcTemplate.execute("TRUNCATE TABLE product_rank_staging")
        return RepeatStatus.FINISHED
    }
}
