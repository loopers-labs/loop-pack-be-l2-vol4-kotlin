package com.loopers.domain.product

import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class ProductDetailComposer(
    private val brandRepositoryPort: BrandRepositoryPort,
    private val stockRepositoryPort: StockRepositoryPort,
    private val likeCountQueryPort: LikeCountQueryPort,
) {
    fun compose(product: Product): ProductDetail {
        val brand = brandRepositoryPort.findById(product.brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
        val stock = stockRepositoryPort.findByProductId(product.id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.")
        val likeCount = likeCountQueryPort.countByProductId(product.id)
        return ProductDetail.of(product, brand, stock, likeCount)
    }

    fun composeAll(products: List<Product>): List<ProductSummary> {
        if (products.isEmpty()) return emptyList()
        val productIds = products.map { it.id }
        val brandIds = products.map { it.brandId }.distinct()
        val brandsById = brandRepositoryPort.findAllByIds(brandIds).associateBy { it.id }
        val stocksByProductId = stockRepositoryPort.findAllByProductIdIn(productIds).associateBy { it.productId }
        val likeCounts = likeCountQueryPort.countsByProductIds(productIds)
        return products.map { product ->
            val brand = brandsById[product.brandId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
            val stock = stocksByProductId[product.id]
                ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.")
            ProductSummary.of(product, brand, stock, likeCounts[product.id] ?: 0L)
        }
    }
}
