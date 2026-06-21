package com.loopers.infrastructure.cache

import com.loopers.domain.product.Product

/**
 * 캐시 전용 DTO. domain 모델([Product])에 Jackson 의존을 두지 않기 위해 분리한다.
 * Kotlin data class 는 jackson-module-kotlin 으로 어노테이션 없이 직렬화/역직렬화된다.
 */
data class ProductCacheModel(
    val id: Long,
    val name: String,
    val price: Long,
    val description: String,
    val brandId: Long,
) {
    fun toDomain(): Product = Product(
        id = id,
        name = name,
        price = price,
        description = description,
        brandId = brandId,
    )

    companion object {
        fun from(product: Product): ProductCacheModel = ProductCacheModel(
            id = product.id,
            name = product.name,
            price = product.price,
            description = product.description,
            brandId = product.brandId,
        )
    }
}
