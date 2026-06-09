package com.loopers.infrastructure.productstat

import com.loopers.domain.productstat.ProductStat

object ProductStatMapper {
    fun toDomain(productStat: ProductStatEntity): ProductStat {
        return ProductStat(
            id = productStat.id,
            productId = productStat.productId,
            likeCount = productStat.likeCount,
        )
    }

    fun toEntity(productStat: ProductStat): ProductStatEntity {
        return ProductStatEntity(
            productId = productStat.productId,
            likeCount = productStat.likeCount,
        )
    }
}
