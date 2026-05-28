package com.loopers.application.brand.dto

import com.loopers.domain.brand.Brand

data class BrandInfo(
    val brandId: Long,
    val name: String,
    val description: String,
    val logoImageUrl: String,
) {
    companion object {
        fun from(brand: Brand): BrandInfo {
            return BrandInfo(
                brandId = brand.id,
                name = brand.name,
                description = brand.description,
                logoImageUrl = brand.logoImageUrl,
            )
        }
    }
}
