package com.loopers.batch.job.ranking

import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = RankingAggregateJobConfig.JOB_NAME)
@Configuration
class RankingAggregateJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val transactionManager: PlatformTransactionManager,
) {
    companion object {
        const val JOB_NAME = "rankingAggregateJob"
        private const val WEEKLY_STEP_NAME = "weeklyRankingStep"
        private const val MONTHLY_STEP_NAME = "monthlyRankingStep"
    }

    @Bean(JOB_NAME)
    fun rankingAggregateJob(
        @Qualifier(WEEKLY_STEP_NAME) weeklyStep: Step,
        @Qualifier(MONTHLY_STEP_NAME) monthlyStep: Step,
    ): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(weeklyStep)
            .next(monthlyStep)
            .listener(jobListener)
            .build()
    }

    @JobScope
    @Bean(WEEKLY_STEP_NAME)
    fun weeklyRankingStep(
        @Qualifier("weeklyRankingTasklet") tasklet: Tasklet,
    ): Step {
        return StepBuilder(WEEKLY_STEP_NAME, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build()
    }

    @JobScope
    @Bean(MONTHLY_STEP_NAME)
    fun monthlyRankingStep(
        @Qualifier("monthlyRankingTasklet") tasklet: Tasklet,
    ): Step {
        return StepBuilder(MONTHLY_STEP_NAME, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build()
    }
}
