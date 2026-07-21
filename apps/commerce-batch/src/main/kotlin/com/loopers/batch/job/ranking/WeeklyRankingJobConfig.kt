package com.loopers.batch.job.ranking

import com.loopers.batch.job.ranking.step.WeeklyRankingItemWriter
import com.loopers.batch.job.ranking.step.WeeklyRankingProcessor
import com.loopers.batch.job.ranking.step.WeeklyRankingReader
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.batch.metrics.ProductMetricsWeekly
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

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Configuration
class WeeklyRankingJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val weeklyRankingReader: WeeklyRankingReader,
    private val weeklyRankingProcessor: WeeklyRankingProcessor,
    private val weeklyRankingItemWriter: WeeklyRankingItemWriter,
) {
    @Bean(JOB_NAME)
    fun weeklyRankingJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(weeklyRankingStep())
            .listener(jobListener)
            .build()

    @JobScope
    @Bean
    fun weeklyRankingStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .chunk<ProductMetricsAggregate, ProductMetricsWeekly>(CHUNK_SIZE, transactionManager)
            .reader(weeklyRankingReader)
            .processor(weeklyRankingProcessor)
            .writer(weeklyRankingItemWriter)
            .listener(stepMonitorListener)
            .listener(weeklyRankingItemWriter)
            .build()

    companion object {
        const val JOB_NAME = "weeklyRankingJob"
        private const val STEP_NAME = "weeklyRankingStep"
        private const val CHUNK_SIZE = 500
    }
}
