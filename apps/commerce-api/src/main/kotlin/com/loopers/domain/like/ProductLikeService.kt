package com.loopers.domain.like

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.domain.productstat.ProductStat
import org.springframework.stereotype.Component

@Component
class ProductLikeService {
    fun like(productStat: ProductStat) {
        productStat.increaseLikeCount()
    }

    fun unlike(productStat: ProductStat) {
        productStat.decreaseLikeCount()
    }

    fun displayLikedProductSummaries(
        likes: List<Like>,
        products: List<Product>,
        brands: List<Brand>,
        productStats: List<ProductStat>,
    ): List<ProductSummary> {
        val productById = products.associateBy { it.id }
        val brandById = brands.associateBy { it.id }
        val statByProductId = productStats.associateBy { it.productId }

        return likes
            .mapNotNull { productById[it.productId] }
            .filter(Product::isDisplayable)
            .mapNotNull { product ->
                val brand = brandById[product.brandId]?.takeIf(Brand::isDisplayable)
                    ?: return@mapNotNull null
                val productStat = statByProductId[product.id]
                    ?: ProductStat(productId = product.id, likeCount = 0)

                ProductSummary(
                    productId = product.id,
                    productName = product.name,
                    price = product.price,
                    imageUrl = product.imageUrl,
                    brandId = brand.id,
                    brandName = brand.name,
                    likeCount = productStat.likeCount,
                )
            }
    }
}
