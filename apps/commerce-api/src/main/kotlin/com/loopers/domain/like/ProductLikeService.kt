package com.loopers.domain.like

import com.loopers.domain.product.Product
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

    fun displayLikedProducts(likes: List<Like>, products: List<Product>): List<Product> {
        val productById = products.associateBy { it.id }

        return likes
            .mapNotNull { productById[it.productId] }
            .filter(Product::isDisplayable)
    }
}
