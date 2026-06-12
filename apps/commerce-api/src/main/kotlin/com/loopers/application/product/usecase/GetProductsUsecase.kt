package com.loopers.application.product.usecase

import com.loopers.application.product.ProductInfo
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
) {
    private val productCatalogDomainService = ProductCatalogDomainService()

    @Transactional(readOnly = true)
    fun execute(query: Query): List<ProductInfo> {
        val products = productRepository.findActiveAll(brandId = query.brandId, sort = query.sort)
        val brandsById = products
            .map { it.brandId }
            .distinct()
            .associateWith { brandId ->
                brandRepository.findActiveById(brandId)
                    ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
            }

        val stockByProductId = productStockRepository.findAllByProductIdIn(products.map { it.id })
            .associate { it.productId to it.quantity }

        return productCatalogDomainService.getDetails(products = products, brandsById = brandsById)
            .map { ProductInfo.from(it, stockByProductId[it.product.id] ?: 0) }
    }

    data class Query(
        val brandId: Long? = null,
        val sort: ProductSort = ProductSort.LATEST,
    )
}
