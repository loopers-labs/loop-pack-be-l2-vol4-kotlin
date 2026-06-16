package com.loopers.infrastructure.product.mapper

import com.loopers.domain.product.model.Product
import com.loopers.infrastructure.product.entity.ProductEntity

object ProductMapper {
    fun toDomain(product: ProductEntity): Product {
        return Product(
            id = product.id,
            brandId = product.brandId,
            name = product.name,
            price = product.price,
            description = product.description,
            imageUrl = product.imageUrl,
            isDeleted = product.isDeleted,
        )
    }

    fun toEntity(product: Product): ProductEntity {
        return ProductEntity(
            brandId = product.brandId,
            name = product.name,
            price = product.price,
            description = product.description,
            imageUrl = product.imageUrl,
            isDeleted = product.isDeleted,
        )
    }
}
