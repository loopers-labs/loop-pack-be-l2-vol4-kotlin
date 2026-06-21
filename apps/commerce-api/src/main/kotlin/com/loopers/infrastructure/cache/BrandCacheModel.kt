package com.loopers.infrastructure.cache

import com.loopers.domain.brand.Brand

/**
 * 브랜드 캐시 전용 DTO. domain 모델([Brand])에 Jackson 의존을 두지 않기 위해 분리한다.
 */
data class BrandCacheModel(
    val id: Long,
    val name: String,
    val description: String,
) {
    fun toDomain(): Brand = Brand(id = id, name = name, description = description)

    companion object {
        fun from(brand: Brand): BrandCacheModel = BrandCacheModel(
            id = brand.id,
            name = brand.name,
            description = brand.description,
        )
    }
}
