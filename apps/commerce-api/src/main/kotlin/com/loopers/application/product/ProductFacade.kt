package com.loopers.application.product

import com.loopers.domain.brand.BrandService
import com.loopers.domain.like.LikeService
import com.loopers.domain.like.ProductId
import com.loopers.domain.product.Level
import com.loopers.domain.product.ProductEvent
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductViewQuery
import com.loopers.domain.product.getProductDomainForUser
import com.loopers.domain.product.TechCategory
import com.loopers.domain.stock.StockService
import com.loopers.domain.user.UserService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.time.ZonedDateTime
import java.util.UUID

@Component
class ProductFacade(
    private val productService: ProductService,
    private val brandService: BrandService,
    private val stockService: StockService,
    private val likeService: LikeService,
    // 캐시/DB 분기·rebuild·fallback 은 query 내부(CQRS read side). Facade 는 모른다.
    private val productViewQuery: ProductViewQuery,
    private val eventPublisher: ApplicationEventPublisher,
    private val userService: UserService,
) {
    fun findProducts(
        brandId: Long?,
        category: TechCategory?,
        level: Level?,
        sort: String,
        pageable: Pageable,
    ): Page<ProductInfo> {
        val page = productViewQuery.findViewPage(brandId, category, level, sort, pageable)
        val ids = page.views.map { it.productId }
        if (ids.isEmpty()) return PageImpl(emptyList(), pageable, page.total)

        // 재고/likeCount 는 캐시 금지 → 실시간 합성 (ADR-0002 D0). like 는 집계 테이블 조회.
        val stocks = stockService.getStocksByProductId(ids)
        val likeCounters = likeService.getLikeCountFromAggregation(ids)
        val content = page.views.map { view ->
            val soldOut = stocks[ProductId(view.productId)]?.isSoldOut() ?: true
            val likeCount = likeCounters[ProductId(view.productId)]?.count ?: 0
            ProductInfo.of(view, likeCount, soldOut)
        }
        return PageImpl(content, pageable, page.total)
    }

    fun getProduct(productId: Long, loginId: String?): ProductInfo {
        val product = productService.getProduct(productId)
        val brand = brandService.getBrandActive(product.brandId)
        val stock = stockService.getStockById(product.id)
        val likeCount = likeService.getLikeCount(product.id)

        val userId = loginId?.let { runCatching { userService.getByLoginId(it).id }.getOrNull() }
        eventPublisher.publishEvent(
            ProductEvent.Viewed(UUID.randomUUID().toString(), productId, userId, ZonedDateTime.now()),
        )

        return getProductDomainForUser(product, brand, stock, likeCount)
            .let { ProductInfo.of(it) }
    }
}
