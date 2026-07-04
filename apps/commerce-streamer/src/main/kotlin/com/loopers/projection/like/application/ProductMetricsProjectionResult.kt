package com.loopers.projection.like.application

data class ProductMetricsProjectionResult(
    val status: ProductMetricsProjectionStatus,
) {
    companion object {
        fun applied(): ProductMetricsProjectionResult =
            ProductMetricsProjectionResult(ProductMetricsProjectionStatus.APPLIED)

        fun duplicate(): ProductMetricsProjectionResult =
            ProductMetricsProjectionResult(ProductMetricsProjectionStatus.DUPLICATE)

        fun stale(): ProductMetricsProjectionResult =
            ProductMetricsProjectionResult(ProductMetricsProjectionStatus.STALE)
    }
}
