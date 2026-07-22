package com.loopers.batch.job.ranking

import com.loopers.application.ranking.RankingCarryOverService
import com.loopers.batch.listener.JobListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersInvalidException
import org.springframework.batch.core.JobParametersValidator
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate

@Configuration
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = DailyRankingCarryOverJobConfig.JOB_NAME)
class DailyRankingCarryOverJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val jobListener: JobListener,
    private val carryOverService: RankingCarryOverService,
) {
    @Bean(JOB_NAME)
    fun dailyRankingCarryOverJob(): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .validator(DailyRankingCarryOverJobParametersValidator())
            .start(dailyRankingCarryOverStep())
            .listener(jobListener)
            .build()
    }

    @Bean
    fun dailyRankingCarryOverStep(): Step {
        return StepBuilder("dailyRankingCarryOverStep", jobRepository)
            .tasklet(dailyRankingCarryOverTasklet(null), transactionManager)
            .build()
    }

    @Bean
    @StepScope
    fun dailyRankingCarryOverTasklet(
        @Value("#{jobParameters['${DailyRankingCarryOverJobParametersValidator.BASE_DATE_PARAMETER}']}") baseDate: LocalDate?,
    ): Tasklet = Tasklet { _, _ ->
        carryOverService.carryOver(requireNotNull(baseDate) { "baseDate must not be null." })
        RepeatStatus.FINISHED
    }

    companion object {
        const val JOB_NAME = "dailyRankingCarryOverJob"
    }
}

class DailyRankingCarryOverJobParametersValidator : JobParametersValidator {
    override fun validate(parameters: JobParameters?) {
        parameters?.getLocalDate(BASE_DATE_PARAMETER)
            ?: throw JobParametersInvalidException("$BASE_DATE_PARAMETER job parameter is required.")
    }

    companion object {
        const val BASE_DATE_PARAMETER = "baseDate"
    }
}
