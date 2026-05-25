package com.loopers.interfaces.api.brand

import com.loopers.application.brand.CreateBrandCommand
import com.loopers.application.brand.UpdateBrandCommand
import com.loopers.domain.common.PageResult

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

    data class BrandsResponse(
        val items: List<BrandV1Dto.BrandResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(result: PageResult<com.loopers.domain.brand.Brand>): BrandsResponse = BrandsResponse(
                items = result.items.map { BrandV1Dto.BrandResponse.from(it) },
                page = result.page,
                size = result.size,
                totalElements = result.totalElements,
                totalPages = result.totalPages,
            )
        }
    }
}
