package com.loopers.ranking.application

import com.loopers.product.domain.ProductRepository
import com.loopers.ranking.infrastructure.ProductRankingDailyJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

@Service
class RankingQueryService(
    private val productRankingDailyJpaRepository: ProductRankingDailyJpaRepository,
    private val productRepository: ProductRepository,
) {
    @Transactional(readOnly = true)
    fun getPage(date: LocalDate?, page: Int, size: Int): RankingPageInfo {
        val rankingDate = date ?: LocalDate.now(KST)
        val rows = productRankingDailyJpaRepository.findByRankingDateOrderByScoreDescProductIdAsc(
            rankingDate,
            PageRequest.of(page - 1, size),
        )
        if (rows.isEmpty()) {
            return RankingPageInfo(rankingDate, page, size, emptyList())
        }
        val products = productRepository.findAllActiveByIdIn(rows.map { it.productId }).associateBy { it.id }
        val startRank = (page - 1).toLong() * size
        val items = rows.mapIndexedNotNull { index, row ->
            products[row.productId]?.let { product ->
                RankingItemInfo(
                    rank = startRank + index + 1,
                    productId = product.id,
                    name = product.name.value,
                    price = product.price.amount,
                    likeCount = product.likeCount,
                    score = row.score.toDouble(),
                )
            }
        }
        return RankingPageInfo(rankingDate, page, size, items)
    }

    @Transactional(readOnly = true)
    fun findTodayRank(productId: Long): Long? {
        val today = LocalDate.now(KST)
        val mine = productRankingDailyJpaRepository.findByRankingDateAndProductId(today, productId) ?: return null
        return productRankingDailyJpaRepository.countByRankingDateAndScoreGreaterThan(today, mine.score) + 1
    }

    private companion object {
        private val KST = ZoneId.of("Asia/Seoul")
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
