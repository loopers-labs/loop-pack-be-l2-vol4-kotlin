package com.loopers.batch.job.productrank

import com.loopers.batch.job.productrank.step.ClearStagingTasklet
import com.loopers.batch.job.productrank.step.RankConfirmTasklet
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

/**
 * productRank* Job들이 공유하는 Step 정의. staging을 쓰는 변형(A: groupBy, B1: stagingUpsert)이
 * 같은 clear/confirm Step을 조립해 쓴다 — 전략이 달라도 "확정" 단계의 동작은 동일해야 실험 비교가 성립한다.
 */
@ConditionalOnExpression("'\${spring.batch.job.name:}'.startsWith('productRank')")
@Configuration
class ProductRankSharedStepConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jdbcTemplate: JdbcTemplate,
    private val stepMonitorListener: StepMonitorListener,
) {
    companion object {
        const val CLEAR_STAGING_STEP = "productRankClearStagingStep"
        const val RANK_CONFIRM_STEP = "productRankConfirmStep"
    }

    @Bean(CLEAR_STAGING_STEP)
    fun clearStagingStep(): Step =
        StepBuilder(CLEAR_STAGING_STEP, jobRepository)
            .tasklet(ClearStagingTasklet(jdbcTemplate), ResourcelessTransactionManager())
            .listener(stepMonitorListener)
            .build()

    @JobScope
    @Bean(RANK_CONFIRM_STEP)
    fun rankConfirmStep(
        @Value("#{jobParameters['period']}") period: String?,
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): Step {
        val rankPeriod = ProductRankJobParams.resolvePeriod(period)
        val window = ProductRankJobParams.resolveWindow(rankPeriod, targetDate)
        return StepBuilder(RANK_CONFIRM_STEP, jobRepository)
            .tasklet(RankConfirmTasklet(jdbcTemplate, rankPeriod, window.aggregatedDate), transactionManager)
            .listener(stepMonitorListener)
            .build()
    }
}
