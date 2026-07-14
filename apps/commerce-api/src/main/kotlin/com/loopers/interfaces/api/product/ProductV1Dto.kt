package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductInfo
import com.loopers.domain.product.Level as DomainLevel
import com.loopers.domain.product.TechCategory as DomainTechCategory

class ProductV1Dto {
    // (id, name, author, category, level, price, likeCount, brandId, brandName, soldOut, rank)
    // stock 수량·status·isbn은 대고객 노출 금지

    enum class TechCategory {
        BACKEND,
        FRONTEND,
        DEVOPS,
        AI_ML,
        DATABASE,
        SECURITY,
        NETWORK,
        ETC;

        fun toDomain(): DomainTechCategory = when (this) {
            BACKEND -> DomainTechCategory.BACKEND
            FRONTEND -> DomainTechCategory.FRONTEND
            DEVOPS -> DomainTechCategory.DEVOPS
            AI_ML -> DomainTechCategory.AI_ML
            DATABASE -> DomainTechCategory.DATABASE
            SECURITY -> DomainTechCategory.SECURITY
            NETWORK -> DomainTechCategory.NETWORK
            ETC -> DomainTechCategory.ETC
        }

        companion object {
            fun from(domain: DomainTechCategory): TechCategory = when (domain) {
                DomainTechCategory.BACKEND -> BACKEND
                DomainTechCategory.FRONTEND -> FRONTEND
                DomainTechCategory.DEVOPS -> DEVOPS
                DomainTechCategory.AI_ML -> AI_ML
                DomainTechCategory.DATABASE -> DATABASE
                DomainTechCategory.SECURITY -> SECURITY
                DomainTechCategory.NETWORK -> NETWORK
                DomainTechCategory.ETC -> ETC
            }
        }
    }

    enum class Level {
        BEGINNER,
        INTERMEDIATE,
        ADVANCED;

        fun toDomain(): DomainLevel = when (this) {
            BEGINNER -> DomainLevel.BEGINNER
            INTERMEDIATE -> DomainLevel.INTERMEDIATE
            ADVANCED -> DomainLevel.ADVANCED
        }

        companion object {
            fun from(domain: DomainLevel): Level = when (domain) {
                DomainLevel.BEGINNER -> BEGINNER
                DomainLevel.INTERMEDIATE -> INTERMEDIATE
                DomainLevel.ADVANCED -> ADVANCED
            }
        }
    }

    data class ProductResponse(
        val productId: Long,
        val name: String,
        val author: String,
        val category: TechCategory,
        val level: Level,
        val price: Int,
        val likeCount: Int,
        val brandId: Long,
        val brandName: String,
        val soldOut: Boolean,
        val rank: Long?,
    ) {
        companion object {
            fun from(info: ProductInfo): ProductResponse = ProductResponse(
                productId = info.productId,
                name = info.name,
                author = info.author,
                category = TechCategory.from(info.category),
                level = Level.from(info.level),
                price = info.price.toInt(),
                likeCount = info.likeCount,
                brandId = info.brandId,
                brandName = info.brandName,
                soldOut = info.soldOut,
                rank = info.rank,
            )
        }
    }
}
