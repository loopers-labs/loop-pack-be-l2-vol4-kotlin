package com.loopers.batch.job.metrics

import com.loopers.batch.job.metrics.step.DailyMetricsItemWriter
import com.loopers.batch.job.metrics.step.DailyMetricsReader
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

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = DailyMetricsJobConfig.JOB_NAME)
@Configuration
class DailyMetricsJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val dailyMetricsReader: DailyMetricsReader,
    private val dailyMetricsItemWriter: DailyMetricsItemWriter,
) {
    @Bean(JOB_NAME)
    fun dailyMetricsJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(dailyMetricsStep())
            .listener(jobListener)
            .build()

    @JobScope
    @Bean
    fun dailyMetricsStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .chunk<ProductDailyDelta, ProductDailyDelta>(CHUNK_SIZE, transactionManager)
            .reader(dailyMetricsReader)
            .writer(dailyMetricsItemWriter)
            .listener(stepMonitorListener)
            .build()

    companion object {
        const val JOB_NAME = "dailyMetricsJob"
        private const val STEP_NAME = "dailyMetricsStep"
        private const val CHUNK_SIZE = 500
    }
}