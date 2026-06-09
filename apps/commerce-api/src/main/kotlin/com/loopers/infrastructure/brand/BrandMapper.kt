package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand

object BrandMapper {
    fun toDomain(brand: BrandEntity): Brand {
        return Brand(
            id = brand.id,
            name = brand.name,
            description = brand.description,
            logoImageUrl = brand.logoImageUrl,
            isDeleted = brand.isDeleted,
        )
    }

    fun toEntity(brand: Brand): BrandEntity {
        return BrandEntity(
            name = brand.name,
            description = brand.description,
            logoImageUrl = brand.logoImageUrl,
            isDeleted = brand.isDeleted,
        )
    }
}
