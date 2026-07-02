package com.loopers.batch.job.payment

import com.loopers.batch.job.payment.step.PaymentReconciliationTasklet
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

@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = PaymentReconciliationJobConfig.JOB_NAME)
@Configuration
class PaymentReconciliationJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
    private val stepMonitorListener: StepMonitorListener,
    private val paymentReconciliationTasklet: PaymentReconciliationTasklet,
) {
    companion object {
        const val JOB_NAME = "paymentReconciliationJob"
        private const val STEP_NAME = "paymentReconciliationStep"
    }

    @Bean(JOB_NAME)
    fun paymentReconciliationJob(): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .incrementer(RunIdIncrementer())
            .start(paymentReconciliationStep())
            .listener(jobListener)
            .build()

    @JobScope
    @Bean(STEP_NAME)
    fun paymentReconciliationStep(): Step =
        StepBuilder(STEP_NAME, jobRepository)
            .tasklet(paymentReconciliationTasklet, ResourcelessTransactionManager())
            .listener(stepMonitorListener)
            .build()
}
