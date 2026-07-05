package com.loopers.infrastructure.product.mapper

import com.loopers.domain.product.model.ProductStat
import com.loopers.infrastructure.product.entity.ProductStatEntity

object ProductStatMapper {
    fun toDomain(productStat: ProductStatEntity): ProductStat {
        return ProductStat(
            id = productStat.id,
            productId = productStat.productId,
            brandId = productStat.brandId,
            likeCount = productStat.likeCount,
            salesCount = productStat.salesCount,
            viewCount = productStat.viewCount,
        )
    }

    fun toEntity(productStat: ProductStat): ProductStatEntity {
        return ProductStatEntity(
            productId = productStat.productId,
            brandId = productStat.brandId,
            likeCount = productStat.likeCount,
            salesCount = productStat.salesCount,
            viewCount = productStat.viewCount,
        )
    }
}
