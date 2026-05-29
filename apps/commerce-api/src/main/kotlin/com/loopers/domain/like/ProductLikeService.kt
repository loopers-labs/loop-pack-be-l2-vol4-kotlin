package com.loopers.domain.like

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
}
