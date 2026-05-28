package com.loopers.infrastructure.product

object ProductMapper {
    fun toDomain(product: Product): com.loopers.domain.product.Product {
        return com.loopers.domain.product.Product(
            id = product.id,
            brandId = product.brandId,
            name = product.name,
            price = product.price,
            description = product.description,
            imageUrl = product.imageUrl,
            isDeleted = product.isDeleted,
        )
    }

    fun toEntity(product: com.loopers.domain.product.Product): Product {
        return Product(
            brandId = product.brandId,
            name = product.name,
            price = product.price,
            description = product.description,
            imageUrl = product.imageUrl,
            isDeleted = product.isDeleted,
        )
    }
}
