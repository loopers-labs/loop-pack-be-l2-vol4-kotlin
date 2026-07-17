package com.loopers.domain.ranking

import com.loopers.domain.product.ProductService
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 랭킹 조회 서비스.
 * ZSET에서 Top-N을 조회하고, 상품 상세 정보를 결합하여 반환한다.
 */
@Component
class RankingQueryService(
    private val rankingQueryRepository: RankingQueryRepository,
    private val productService: ProductService,
) {

    /**
     * 랭킹 페이지를 조회한다.
     * 상품 ID뿐 아니라 상품명, 가격 등 상세 정보를 함께 반환한다.
     *
     * @param date 조회 대상 날짜 (yyyyMMdd). null이면 오늘.
     * @param page 페이지 번호 (0-based)
     * @param size 페이지 크기
     * @return 랭킹 상품 목록
     */
    fun getRankingPage(date: String?, page: Int, size: Int): List<RankingProductInfo> {
        val targetDate = date ?: todayDate()
        val offset = (page * size).toLong()

        val entries = rankingQueryRepository.getTopN(targetDate, offset, size.toLong())
        if (entries.isEmpty()) return emptyList()

        val productIds = entries.map { it.productId }
        val products = productService.getProductsByIds(productIds)

        return entries.mapIndexed { index, entry ->
            val product = products[entry.productId]
            RankingProductInfo(
                rank = offset + index + 1,
                productId = entry.productId,
                productName = product?.name,
                price = product?.price,
                score = entry.score,
            )
        }
    }

    /**
     * 특정 상품의 오늘 순위를 조회한다.
     *
     * @param productId 상품 ID
     * @return 순위 (1-based). 랭킹에 없으면 null.
     */
    fun getProductRank(productId: Long): RankingInfo? {
        val today = todayDate()
        val rank = rankingQueryRepository.getRank(today, productId) ?: return null
        val score = rankingQueryRepository.getScore(today, productId)
        return RankingInfo(rank = rank, score = score ?: 0.0)
    }

    private fun todayDate(): String = LocalDate.now().format(DATE_FORMAT)

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}

/**
 * 랭킹 페이지 항목 정보.
 */
data class RankingProductInfo(
    val rank: Long,
    val productId: Long,
    val productName: String?,
    val price: Long?,
    val score: Double,
)

/**
 * 개별 상품의 랭킹 정보.
 */
data class RankingInfo(
    val rank: Long,
    val score: Double,
)
