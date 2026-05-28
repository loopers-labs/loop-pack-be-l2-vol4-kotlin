package com.loopers.interfaces.api.brand

import com.loopers.application.brand.dto.BrandInfo

class BrandV1Dto {
    data class BrandResponse(
        val brandId: Long,
        val name: String,
        val description: String,
        val logoImageUrl: String,
    ) {
        companion object {
            fun from(info: BrandInfo): BrandResponse {
                return BrandResponse(
                    brandId = info.brandId,
                    name = info.name,
                    description = info.description,
                    logoImageUrl = info.logoImageUrl,
                )
            }
        }
    }
}
