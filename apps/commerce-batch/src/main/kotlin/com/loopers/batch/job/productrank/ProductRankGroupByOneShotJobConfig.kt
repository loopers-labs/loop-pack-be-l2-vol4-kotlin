package com.loopers.batch.job.productrank

import com.loopers.batch.job.productrank.step.OneShotAggregateTasklet
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

/**
 * 변형 A2 — DB 단발 집계. A1(productRankGroupByJob)이 페이징 때문에 매 페이지 GROUP BY를
 * 반복하는 것과 달리, 기간 전체를 `INSERT INTO staging SELECT ... GROUP BY` 한 문장으로 끝낸다.
 * "DB 집계" 전략의 온전한 비용을 측정하기 위한 대조군 — chunk 재시작성 포기가 트레이드오프.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = ProductRankGroupByOneShotJobConfig.JOB_NAME)
@Configuration
class ProductRankGroupByOneShotJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val jdbcTemplate: JdbcTemplate,
    private val rankWeightLoader: RankWeightLoader,
) {
    companion object {
        const val JOB_NAME = "productRankGroupByOneShotJob"
        private const val STEP_AGGREGATE = "productRankGroupByOneShotAggregateStep"
    }

    @Bean(JOB_NAME)
    fun productRankGroupByOneShotJob(
        @Qualifier(ProductRankSharedStepConfig.CLEAR_STAGING_STEP) clearStagingStep: Step,
        @Qualifier(ProductRankSharedStepConfig.RANK_CONFIRM_STEP) rankConfirmStep: Step,
    ): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .validator(ProductRankJobParams.validator())
            .start(clearStagingStep)
            .next(aggregateStep())
            .next(rankConfirmStep)
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_AGGREGATE)
    fun aggregateStep(
        @Value("#{jobParameters['period']}") period: String? = null,
        @Value("#{jobParameters['targetDate']}") targetDate: String? = null,
    ): Step {
        val window = ProductRankJobParams.resolveWindow(ProductRankJobParams.resolvePeriod(period), targetDate)
        val weights = rankWeightLoader.loadActive()
        return StepBuilder(STEP_AGGREGATE, jobRepository)
            .tasklet(OneShotAggregateTasklet(jdbcTemplate, window, weights), transactionManager)
            .listener(stepMonitorListener)
            .build()
    }
}
