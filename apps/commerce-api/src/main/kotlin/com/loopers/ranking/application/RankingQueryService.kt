package com.loopers.ranking.application

import com.loopers.product.domain.ProductRepository
import com.loopers.ranking.domain.RankingKeys
import com.loopers.ranking.infrastructure.RankingZSetReader
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RankingQueryService(
    private val rankingZSetReader: RankingZSetReader,
    private val productRepository: ProductRepository,
) {
    fun getPage(date: LocalDate?, page: Int, size: Int): RankingPageInfo {
        val rankingDate = date ?: LocalDate.now(RankingKeys.KST)
        val offset = (page - 1).toLong() * size
        val rows = rankingZSetReader.reverseRange(RankingKeys.today(rankingDate), offset, offset + size - 1)
        if (rows.isEmpty()) {
            return RankingPageInfo(rankingDate, page, size, emptyList())
        }
        val products = productRepository.findAllActiveByIdIn(rows.map { it.productId }).associateBy { it.id }
        val items = rows.mapIndexedNotNull { index, row ->
            products[row.productId]?.let { product ->
                RankingItemInfo(
                    rank = offset + index + 1,
                    productId = product.id,
                    name = product.name.value,
                    price = product.price.amount,
                    likeCount = product.likeCount,
                    score = row.score,
                )
            }
        }
        return RankingPageInfo(rankingDate, page, size, items)
    }

    fun findTodayRank(productId: Long): Long? {
        val key = RankingKeys.today(LocalDate.now(RankingKeys.KST))
        val score = rankingZSetReader.score(key, productId) ?: return null
        return rankingZSetReader.countHigherThan(key, score) + 1
    }
}

data class RankingPageInfo(
    val date: LocalDate,
    val page: Int,
    val size: Int,
    val items: List<RankingItemInfo>,
)

data class RankingItemInfo(
    val rank: Long,
    val productId: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val score: Double,
)
