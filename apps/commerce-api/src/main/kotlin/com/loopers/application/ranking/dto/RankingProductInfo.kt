package com.loopers.application.ranking.dto

import com.loopers.domain.product.dto.ProductSummary
import com.loopers.domain.ranking.RankingEntry

data class RankingProductInfo(
    val productId: Long,
    val productName: String,
    val price: Long,
    val imageUrl: String,
    val brandId: Long,
    val brandName: String,
    val likeCount: Long,
    val rank: Long,
    val score: Double,
) {
    companion object {
        fun from(entry: RankingEntry, product: ProductSummary): RankingProductInfo {
            return RankingProductInfo(
                productId = product.productId,
                productName = product.productName,
                price = product.price,
                imageUrl = product.imageUrl,
                brandId = product.brandId,
                brandName = product.brandName,
                likeCount = product.likeCount,
                rank = entry.rank,
                score = entry.score,
            )
        }
    }
}
