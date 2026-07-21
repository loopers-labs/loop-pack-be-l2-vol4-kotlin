package com.loopers.batch.job.productranking

data class ProductRankingWeights(
    val view: Double,
    val like: Double,
    val sales: Double,
) {
    init {
        require(view.isFinite() && view >= 0.0) { "Product ranking view weight must be non-negative finite." }
        require(like.isFinite() && like >= 0.0) { "Product ranking like weight must be non-negative finite." }
        require(sales.isFinite() && sales >= 0.0) { "Product ranking sales weight must be non-negative finite." }
    }

    companion object {
        const val VIEW_CONTEXT_KEY = "productRanking.viewWeight"
        const val LIKE_CONTEXT_KEY = "productRanking.likeWeight"
        const val SALES_CONTEXT_KEY = "productRanking.salesWeight"
    }
}
