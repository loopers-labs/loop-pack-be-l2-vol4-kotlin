package com.loopers.application.product.usecase

import com.loopers.application.product.ProductCacheRepository
import com.loopers.application.product.ProductInfo
import com.loopers.application.product.ProductPageInfo
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductCatalogDomainService
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetProductsUsecase(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStockRepository: ProductStockRepository,
    private val productCacheRepository: ProductCacheRepository,
) {
    private val productCatalogDomainService = ProductCatalogDomainService()

    @Transactional(readOnly = true)
    fun execute(query: Query): ProductPageInfo {
        val cacheQuery = query.toCacheQuery()
        productCacheRepository.getList(cacheQuery)?.let { return it }

        val page = productRepository.findActiveAll(
            brandId = query.brandId,
            sort = query.sort,
            pageable = query.sort.toPageable(page = query.page, size = query.size),
        )
        val products = page.content
        val brandsById = products
            .map { it.brandId }
            .distinct()
            .associateWith { brandId ->
                brandRepository.findActiveById(brandId)
                    ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
            }

        val stockByProductId = productStockRepository.findAllByProductIdIn(products.map { it.id })
            .associate { it.productId to it.quantity }

        val items = productCatalogDomainService.getDetails(products = products, brandsById = brandsById)
            .map { ProductInfo.from(it, stockByProductId[it.product.id] ?: 0) }

        val productPageInfo = ProductPageInfo(
            items = items,
            page = page.number,
            size = page.size,
            totalCount = page.totalElements,
            totalPages = page.totalPages,
        )
        productCacheRepository.putList(cacheQuery, productPageInfo)
        return productPageInfo
    }

    data class Query(
        val brandId: Long? = null,
        val sort: ProductSort = ProductSort.LATEST,
        val page: Int = 0,
        val size: Int = 20,
    ) {
        init {
            if (page < 0) throw CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.")
            if (size !in 1..100) throw CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상 100 이하여야 합니다.")
        }

        fun toCacheQuery(): ProductCacheRepository.ProductListCacheQuery {
            return ProductCacheRepository.ProductListCacheQuery(
                brandId = brandId,
                sort = sort,
                page = page,
                size = size,
            )
        }
    }
}
