package com.loopers.batch.job.productranking

import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener

class ProductRankingWeightSnapshotListener(
    private val weightReader: ProductRankingWeightReader,
) : JobExecutionListener {
    override fun beforeJob(jobExecution: JobExecution) {
        val weights = weightReader.read()
        jobExecution.executionContext.putDouble(ProductRankingWeights.VIEW_CONTEXT_KEY, weights.view)
        jobExecution.executionContext.putDouble(ProductRankingWeights.LIKE_CONTEXT_KEY, weights.like)
        jobExecution.executionContext.putDouble(ProductRankingWeights.SALES_CONTEXT_KEY, weights.sales)
    }
}
