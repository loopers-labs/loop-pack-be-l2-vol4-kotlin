package com.loopers.application.product.usecase

import com.loopers.application.product.ProductCacheRepository
import com.loopers.application.product.ProductInfo
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductCatalogDomainService
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetProductDetailUsecase(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val productStockRepository: ProductStockRepository,
    private val productCacheRepository: ProductCacheRepository,
) {
    private val log = LoggerFactory.getLogger(GetProductDetailUsecase::class.java)
    private val productCatalogDomainService = ProductCatalogDomainService()

    @Transactional(readOnly = true)
    fun execute(productId: Long): ProductInfo {
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
}
