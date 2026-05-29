package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class BrandInfo(
    val id: Long,
    val name: String,
    val description: String,
    val logoImageUrl: String?,
) {
    companion object {
        fun from(brand: Brand): BrandInfo {
            return BrandInfo(
                id = brand.id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "브랜드 ID가 존재하지 않습니다."),
                name = brand.name,
                description = brand.description,
                logoImageUrl = brand.logoImageUrl,
            )
        }
    }
}
