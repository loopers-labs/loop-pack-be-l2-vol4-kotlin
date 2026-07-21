package com.loopers.batch.job.ranking

import com.loopers.batch.job.ranking.step.MonthlyRankingItemWriter
import com.loopers.batch.job.ranking.step.MonthlyRankingProcessor
import com.loopers.batch.job.ranking.step.MonthlyRankingReader
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.batch.metrics.ProductMetricsMonthly
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

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = MonthlyRankingJobConfig.JOB_NAME)
@Configuration
class MonthlyRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val monthlyRankingReader: MonthlyRankingReader,
    private val monthlyRankingProcessor: MonthlyRankingProcessor,
    private val monthlyRankingItemWriter: MonthlyRankingItemWriter,
) {
    @Bean(JOB_NAME)
    fun monthlyRankingJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(monthlyRankingStep())
            .listener(jobListener)
            .build()

    @JobScope
    @Bean
    fun monthlyRankingStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .chunk<ProductMetricsAggregate, ProductMetricsMonthly>(CHUNK_SIZE, transactionManager)
            .reader(monthlyRankingReader)
            .processor(monthlyRankingProcessor)
            .writer(monthlyRankingItemWriter)
            .listener(stepMonitorListener)
            .listener(monthlyRankingItemWriter)
            .build()

    companion object {
        const val JOB_NAME = "monthlyRankingJob"
        private const val STEP_NAME = "monthlyRankingStep"
        private const val CHUNK_SIZE = 500
    }
}