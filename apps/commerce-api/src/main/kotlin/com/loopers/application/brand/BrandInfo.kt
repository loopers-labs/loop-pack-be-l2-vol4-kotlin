package com.loopers.application.brand

import com.loopers.domain.brand.BrandModel

data class BrandInfo(
    val id: Long,
    val name: String,
    val description: String,
) {
    companion object {
        fun from(brand: BrandModel): BrandInfo {
            return BrandInfo(
                id = brand.id,
                name = brand.name,
                description = brand.description,
            )
        }
    }
}
