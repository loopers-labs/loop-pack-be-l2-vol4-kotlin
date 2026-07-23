package com.loopers.application.ranking

import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.LikeService
import com.loopers.domain.like.ProductId
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.ranking.RankingService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class RankingFacade(
    private val rankingService: RankingService,
    private val productService: ProductService,
    private val brandService: BrandService,
    private val likeService: LikeService,
    private val clock: Clock,
) {
    fun getRankings(date: LocalDate?, pageable: Pageable): Page<RankingInfo> {
        val targetDate = date ?: LocalDate.now(clock)
        val rankingPage = rankingService.getRankingPage(targetDate, pageable)
        if (rankingPage.entries.isEmpty()) return PageImpl(emptyList(), pageable, rankingPage.total)

        val productIds = rankingPage.entries.map { it.productId }
        val products = productService.getProducts(productIds)
            .filter { it.status == ProductStatus.ACTIVE }
            .associateBy { it.id }
        val brands = brandService.getBrandsByIds(products.values.map { it.brandId }.distinct())
        val likeCounts = likeService.getLikeCountFromAggregation(productIds)

        val content = rankingPage.entries.mapNotNull { entry ->
            val product = products[entry.productId] ?: return@mapNotNull null
            RankingInfo.of(
                rank = entry.rank,
                productId = entry.productId,
                name = product.name,
                brandName = brands[product.brandId]?.name ?: "unknown",
                price = product.price.toInt(),
                likeCount = likeCounts[ProductId(entry.productId)]?.count ?: 0,
            )
        }
        return PageImpl(content, pageable, rankingPage.total)
    }
}
