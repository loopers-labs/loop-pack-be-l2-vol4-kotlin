package com.loopers.domain.brand.application.info

import com.loopers.domain.brand.model.BrandModel

data class BrandInfo(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(brand: BrandModel): BrandInfo = BrandInfo(
            id = brand.id,
            name = brand.name.value,
        )
    }
}
