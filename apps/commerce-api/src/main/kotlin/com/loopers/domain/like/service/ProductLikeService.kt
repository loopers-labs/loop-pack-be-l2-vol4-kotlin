package com.loopers.domain.like.service

import com.loopers.domain.brand.model.Brand
import com.loopers.domain.like.model.Like
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.domain.product.model.Product
import com.loopers.domain.product.model.ProductStat
import org.springframework.stereotype.Component

@Component
class ProductLikeService {
    fun displayLikedProductSummaries(
        likes: List<Like>,
        products: List<Product>,
        brands: List<Brand>,
        productStats: List<ProductStat>,
    ): List<ProductSummary> {
        val productById = products.associateBy { it.id }
        val brandById = brands.associateBy { it.id }
        val statByProductId = productStats.associateBy { it.productId }

        return likes.mapNotNull { like ->
            val product = productById[like.productId]
                ?: return@mapNotNull null

            val brand = brandById[product.brandId]
                ?: return@mapNotNull null

            val productStat = statByProductId[product.id]
                ?: ProductStat.empty(product.id)

            ProductSummary.from(
                product = product,
                brand = brand,
                productStat = productStat,
            )
        }
    }
}
