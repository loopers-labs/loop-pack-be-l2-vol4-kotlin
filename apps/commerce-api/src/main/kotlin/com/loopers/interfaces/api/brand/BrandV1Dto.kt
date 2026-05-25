package com.loopers.interfaces.api.brand

import com.loopers.domain.brand.Brand

class BrandV1Dto {
    data class BrandResponse(
        val id: Long,
        val name: String,
        val description: String,
    ) {
        companion object {
            fun from(brand: Brand): BrandResponse = BrandResponse(
                id = brand.id,
                name = brand.name,
                description = brand.description,
            )
        }
    }
}
