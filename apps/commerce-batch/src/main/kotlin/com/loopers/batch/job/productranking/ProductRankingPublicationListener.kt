package com.loopers.batch.job.productranking

import com.loopers.domain.productrank.ProductRankPublicationRepository
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import java.util.UUID

class ProductRankingPublicationListener(
    private val period: ProductRankingPeriod,
    private val publicationRepository: ProductRankPublicationRepository,
) : JobExecutionListener {
    override fun afterJob(jobExecution: JobExecution) {
        if (jobExecution.status != BatchStatus.COMPLETED) {
            return
        }
        val baseDate = jobExecution.jobParameters.getLocalDate(ProductRankingJobParametersValidator.BASE_DATE_PARAMETER)
            ?: return
        publicationRepository.publish(
            period = period.name,
            baseDate = baseDate,
            generationId = UUID.randomUUID().toString(),
        )
    }
}
