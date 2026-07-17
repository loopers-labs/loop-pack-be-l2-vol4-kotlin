package com.loopers.application.product.usecase

import com.loopers.application.product.ProductCacheRepository
import com.loopers.application.product.ProductInfo
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductCatalogDomainService
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.product.ProductViewedEvent
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingQueryRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class GetProductDetailUsecase(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStockRepository: ProductStockRepository,
    private val productCacheRepository: ProductCacheRepository,
    private val rankingQueryRepository: RankingQueryRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(GetProductDetailUsecase::class.java)
    private val productCatalogDomainService = ProductCatalogDomainService()

    @Transactional(readOnly = true)
    fun execute(productId: Long): ProductInfo {
        val info = loadProductInfo(productId)
        eventPublisher.publishEvent(ProductViewedEvent(productId = productId))
        // rank는 실시간 값 — 캐시(ProductInfo TTL 60s)와 무관하게 매 요청 조회, 장애 시 null(스펙 §7)
        return info.copy(rank = currentDailyRank(productId))
    }

    private fun loadProductInfo(productId: Long): ProductInfo {
        runCatching { productCacheRepository.getDetail(productId) }
            .onFailure { log.warn("Failed to get product detail cache. productId={}", productId, it) }
            .getOrNull()
            ?.let { return it }

        val product = productRepository.findActiveById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        val brand = brandRepository.findActiveById(product.brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
        val stockQuantity = productStockRepository.findByProductId(productId)?.quantity ?: 0

        val productInfo = productCatalogDomainService.getDetail(product = product, brand = brand)
            .let { ProductInfo.from(it, stockQuantity) }
        runCatching { productCacheRepository.putDetail(productId = productId, product = productInfo) }
            .onFailure { log.warn("Failed to put product detail cache. productId={}", productId, it) }
        return productInfo
    }

    private fun currentDailyRank(productId: Long): Long? =
        runCatching {
            val date = RankingPeriod.DAILY.resolveDate(null, ZonedDateTime.now())
            rankingQueryRepository.rank(RankingPeriod.DAILY.key(date), productId)?.plus(1)
        }.onFailure { log.warn("Failed to get product rank. productId={}", productId, it) }
            .getOrNull()
}
