package com.loopers.infrastructure.brand

object BrandMapper {
    fun toDomain(brand: Brand): com.loopers.domain.brand.Brand {
        return com.loopers.domain.brand.Brand(
            id = brand.id,
            name = brand.name,
            description = brand.description,
            logoImageUrl = brand.logoImageUrl,
            isDeleted = brand.isDeleted,
        )
    }

    fun toEntity(brand: com.loopers.domain.brand.Brand): Brand {
        return Brand(
            name = brand.name,
            description = brand.description,
            logoImageUrl = brand.logoImageUrl,
            isDeleted = brand.isDeleted,
        )
    }
}
