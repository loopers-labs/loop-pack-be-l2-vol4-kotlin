package com.loopers.application.ranking

import com.loopers.application.ranking.result.RankedProductResult
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.support.page.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

/**
 * 랭킹판 조회를 상품 정보와 조립한다 — ZSET 에서 순위·점수를 읽고, 상품·브랜드를 배치 조회해 목록에 필요한 정보를 붙인다.
 * 삭제/부재 상품은 목록에서 제외한다. N+1 없이 id 묶음으로 한 번에 조회한다.
 */
@Component
class RankingFacade(
    private val rankingRepository: RankingRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
) {
    @Transactional(readOnly = true)
    fun getRanking(date: String?, page: Int, size: Int): PageResult<RankedProductResult> {
        val key = RankingKey.of(date, LocalDate.now(SEOUL))
        val total = rankingRepository.size(key)
        val totalPages = if (size == 0) 0 else ceil(total.toDouble() / size).toInt()
        val offset = page.toLong() * size
        val entries = rankingRepository.topN(key, offset, size.toLong())
        val content = assemble(entries, offset)
        return PageResult(content, page, size, total, totalPages)
    }

    private fun assemble(entries: List<com.loopers.domain.ranking.RankedEntry>, offset: Long): List<RankedProductResult> {
        if (entries.isEmpty()) return emptyList()
        val products = productRepository.findAllByIds(entries.map { it.productId }).associateBy { it.id }
        val brands = brandRepository.findAllByIds(products.values.map { it.brandId }.distinct()).associateBy { it.id }

        return entries.mapIndexedNotNull { index, entry ->
            val product = products[entry.productId] ?: return@mapIndexedNotNull null
            val brand = brands[product.brandId] ?: return@mapIndexedNotNull null
            RankedProductResult(
                productId = product.id,
                name = product.name.value,
                price = product.price.value,
                brandName = brand.name.value,
                likeCount = product.likeCount,
                rank = offset + index + 1,
                score = entry.score,
            )
        }
    }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
