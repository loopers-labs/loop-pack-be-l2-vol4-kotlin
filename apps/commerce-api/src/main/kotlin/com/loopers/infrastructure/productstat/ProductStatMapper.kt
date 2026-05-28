package com.loopers.infrastructure.productstat

object ProductStatMapper {
    fun toDomain(productStat: ProductStat): com.loopers.domain.productstat.ProductStat {
        return com.loopers.domain.productstat.ProductStat(
            id = productStat.id,
            productId = productStat.productId,
            likeCount = productStat.likeCount,
        )
    }

    fun toEntity(productStat: com.loopers.domain.productstat.ProductStat): ProductStat {
        return ProductStat(
            productId = productStat.productId,
            likeCount = productStat.likeCount,
        )
    }
}
