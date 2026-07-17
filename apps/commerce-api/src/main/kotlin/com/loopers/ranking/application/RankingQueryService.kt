package com.loopers.ranking.application

import com.loopers.product.domain.ProductRepository
import com.loopers.ranking.domain.RankingKeys
import com.loopers.ranking.infrastructure.RankingFallbackDailyJpaRepository
import com.loopers.ranking.infrastructure.RankingScore
import com.loopers.ranking.infrastructure.RankingZSetReader
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RankingQueryService(
    private val rankingZSetReader: RankingZSetReader,
    private val rankingFallbackDailyJpaRepository: RankingFallbackDailyJpaRepository,
    private val productRepository: ProductRepository,
) {
    private val logger = LoggerFactory.getLogger(RankingQueryService::class.java)

    fun getPage(date: LocalDate?, page: Int, size: Int): RankingPageInfo {
        val rankingDate = date ?: LocalDate.now(RankingKeys.KST)
        val offset = (page - 1).toLong() * size
        val rows = try {
            rankingZSetReader.reverseRange(RankingKeys.today(rankingDate), offset, offset + size - 1)
        } catch (e: Exception) {
            fallbackGuard(e)
            rankingFallbackDailyJpaRepository
                .findByRankingDateOrderByScoreDescProductIdAsc(rankingDate, PageRequest.of(page - 1, size))
                .map { RankingScore(it.productId, it.score.toDouble()) }
        }
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
        val today = LocalDate.now(RankingKeys.KST)
        return try {
            val key = RankingKeys.today(today)
            val score = rankingZSetReader.score(key, productId) ?: return null
            rankingZSetReader.countHigherThan(key, score) + 1
        } catch (e: Exception) {
            fallbackGuard(e)
            val mine = rankingFallbackDailyJpaRepository.findByRankingDateAndProductId(today, productId) ?: return null
            rankingFallbackDailyJpaRepository.countByRankingDateAndScoreGreaterThan(today, mine.score) + 1
        }
    }

    private fun fallbackGuard(e: Exception) {
        if (e !is DataAccessException && e !is CallNotPermittedException) {
            throw e
        }
        logger.warn("랭킹 Redis 읽기 실패 — MySQL fallback 전환: {}", e.javaClass.simpleName)
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
