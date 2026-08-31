package com.loopers.batch.job.ranking

import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
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
import javax.sql.DataSource

// Hides: chunk-2 transaction boundaries and Spring Batch restart metadata wiring.
@Configuration
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingJobConfig {
    @Bean(JOB_NAME)
    fun weeklyRankingJob(repo: JobRepository, @Qualifier(STEP_NAME) step: Step): Job =
        JobBuilder(JOB_NAME, repo).start(step).build()

    @Bean(STEP_NAME)
    fun weeklyRankingStep(
        repo: JobRepository,
        transactionManager: PlatformTransactionManager,
        reader: RankingItemReader,
        processor: RankingItemProcessor,
        writer: RankingItemWriter,
    ): Step = StepBuilder(STEP_NAME, repo)
        .chunk<RankingItemReader.SourceRow, RankingItemReader.SourceRow>(2, transactionManager)
        .reader(reader).processor(processor).writer(writer).build()

    @Bean @StepScope
    fun rankingItemReader(dataSource: DataSource) = RankingItemReader(JdbcTemplate(dataSource))

    @Bean @StepScope
    fun rankingItemProcessor(@Value("#{jobParameters['injectFailure'] ?: false}") injectFailure: Boolean) =
        RankingItemProcessor(injectFailure)

    @Bean @StepScope
    fun rankingItemWriter(dataSource: DataSource) = RankingItemWriter(JdbcTemplate(dataSource))

    companion object { const val JOB_NAME = "weeklyRankingJob"; const val STEP_NAME = "weeklyRankingStep" }
}
