package com.loopers.batch.job.order

import com.loopers.batch.job.order.step.OrderReservationExpirationTasklet
import com.loopers.batch.listener.JobListener
import com.loopers.batch.listener.StepMonitorListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.JobScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = OrderReservationExpirationJobConfig.JOB_NAME)
@Configuration
class OrderReservationExpirationJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val tasklet: OrderReservationExpirationTasklet,
) {
    companion object {
        const val JOB_NAME = "orderReservationExpirationJob"
        private const val STEP_NAME = "orderReservationExpirationStep"
    }

    @Bean(JOB_NAME)
    fun orderReservationExpirationJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(orderReservationExpirationStep())
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_NAME)
    fun orderReservationExpirationStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .tasklet(tasklet, ResourcelessTransactionManager())
            .listener(stepMonitorListener)
            .build()
}
