package com.loopers.interfaces.api.brand.dto

import com.loopers.application.brand.dto.BrandCreateCommand
import com.loopers.application.brand.dto.BrandInfo
import com.loopers.application.brand.dto.BrandUpdateCommand

class AdminBrandV1Dto {
    data class CreateBrandRequest(
        val name: String,
        val description: String,
        val logoImageUrl: String,
    ) {
        fun toCommand(): BrandCreateCommand {
            return BrandCreateCommand(
                name = name,
                description = description,
                logoImageUrl = logoImageUrl,
            )
        }
    }

    data class UpdateBrandRequest(
        val name: String,
        val description: String,
        val logoImageUrl: String,
    ) {
        fun toCommand(): BrandUpdateCommand {
            return BrandUpdateCommand(
                name = name,
                description = description,
                logoImageUrl = logoImageUrl,
            )
        }
    }

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
