package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.ProductDetailImage
import com.loopers.domain.catalog.ProductDetailImageRepository
import org.springframework.stereotype.Component

@Component
class ProductDetailImageRepositoryImpl(
    private val productDetailImageJpaRepository: ProductDetailImageJpaRepository,
) : ProductDetailImageRepository {
    override fun saveAll(images: List<ProductDetailImage>): List<ProductDetailImage> =
        productDetailImageJpaRepository.saveAll(images)

    override fun findByProductId(productId: Long): List<ProductDetailImage> =
        productDetailImageJpaRepository.findAllByProductIdAndDeletedAtIsNullOrderBySortOrderAsc(productId)

    override fun softDeleteByProductId(productId: Long) {
        productDetailImageJpaRepository.findAllByProductIdAndDeletedAtIsNullOrderBySortOrderAsc(productId)
            .forEach { it.delete() }
    }
}
