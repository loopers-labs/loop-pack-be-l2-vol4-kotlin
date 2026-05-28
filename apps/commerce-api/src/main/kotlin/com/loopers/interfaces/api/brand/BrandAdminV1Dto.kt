package com.loopers.interfaces.api.brand

import com.loopers.application.brand.CreateBrandCommand
import com.loopers.application.brand.UpdateBrandCommand
import com.loopers.domain.brand.Brand

class BrandAdminV1Dto {
    data class CreateBrandRequest(
        val name: String,
        val description: String,
    ) {
        fun toCommand(): CreateBrandCommand = CreateBrandCommand(name = name, description = description)
    }

    data class UpdateBrandRequest(
        val name: String,
        val description: String,
    ) {
        fun toCommand(id: Long): UpdateBrandCommand = UpdateBrandCommand(id = id, name = name, description = description)
    }

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
