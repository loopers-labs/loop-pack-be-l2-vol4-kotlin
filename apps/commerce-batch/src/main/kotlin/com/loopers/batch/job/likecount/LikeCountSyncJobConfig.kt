package com.loopers.batch.job.likecount

import com.loopers.batch.job.likecount.step.LikeCountSyncTasklet
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

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = LikeCountSyncJobConfig.JOB_NAME)
@Configuration
class LikeCountSyncJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val likeCountSyncTasklet: LikeCountSyncTasklet,
    private val transactionManager: PlatformTransactionManager,
) {
    companion object {
        const val JOB_NAME = "likeCountSyncJob"
        private const val STEP_NAME = "likeCountSyncStep"
    }

    @Bean(JOB_NAME)
    fun likeCountSyncJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(likeCountSyncStep())
            .listener(jobListener)
            .build()
    }

    @JobScope
    @Bean(STEP_NAME)
    fun likeCountSyncStep(): Step {
        return StepBuilder(STEP_NAME, jobRepository)
            .tasklet(likeCountSyncTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build()
    }
}
