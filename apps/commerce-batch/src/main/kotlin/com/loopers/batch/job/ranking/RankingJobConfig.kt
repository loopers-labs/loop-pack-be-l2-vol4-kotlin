package com.loopers.batch.job.ranking

import com.loopers.batch.job.ranking.step.RankingAggregationTasklet
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * 상품 랭킹 집계 배치 Job 설정.
 * product_metrics → score 계산 → TOP 100 → mv_product_rank 적재.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = RankingJobConfig.JOB_NAME)
@Configuration
class RankingJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val transactionManager: PlatformTransactionManager,
    private val rankingAggregationTasklet: RankingAggregationTasklet,
) {
    companion object {
        const val JOB_NAME = "rankingAggregationJob"
        private const val STEP_NAME = "rankingAggregationStep"
    }

    @Bean(JOB_NAME)
    fun rankingAggregationJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(rankingAggregationStep())
            .listener(jobListener)
            .build()
    }

    @JobScope
    @Bean(STEP_NAME)
    fun rankingAggregationStep(): Step {
        return StepBuilder(STEP_NAME, jobRepository)
            .tasklet(rankingAggregationTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build()
    }
}
