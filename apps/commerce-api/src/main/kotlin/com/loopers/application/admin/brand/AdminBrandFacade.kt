package com.loopers.application.admin.brand

import com.loopers.application.brand.BrandService
import com.loopers.application.brand.dto.BrandCreateCommand
import com.loopers.application.brand.dto.BrandInfo
import com.loopers.application.brand.dto.BrandUpdateCommand
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class AdminBrandFacade(
    private val brandService: BrandService,
) {
    fun getBrands(page: Int, size: Int): Page<BrandInfo> {
        return brandService.getBrands(page = page, size = size)
            .map(BrandInfo::from)
    }

    fun getBrand(brandId: Long): BrandInfo {
        return brandService.getDisplayableBrand(brandId)
            .let(BrandInfo::from)
    }

    fun createBrand(command: BrandCreateCommand): BrandInfo {
        return brandService.createBrand(command)
            .let(BrandInfo::from)
    }

    fun updateBrand(brandId: Long, command: BrandUpdateCommand): BrandInfo {
        return brandService.updateBrand(brandId = brandId, command = command)
            .let(BrandInfo::from)
    }
}
