package com.loopers.batch.job.productranking

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "commerce.product-ranking.batch")
data class ProductRankingBatchProperties(
    val metric: Metric = Metric(),
    val cache: Cache = Cache(),
) {
    init {
        require(metric.fetchSize > 0) { "Product ranking metric fetch size must be positive." }
        require(metric.chunkSize > 0) { "Product ranking metric chunk size must be positive." }
        require(cache.weeklyTtlDays > 0) { "Product ranking weekly cache TTL must be positive." }
        require(cache.monthlyTtlDays > 0) { "Product ranking monthly cache TTL must be positive." }
    }

    data class Metric(
        val fetchSize: Int = 1_000,
        val chunkSize: Int = 500,
    )

    data class Cache(
        val weeklyTtlDays: Long = 8,
        val monthlyTtlDays: Long = 32,
    )
}
