package com.loopers.application.catalog

import com.loopers.application.brand.BrandService
import com.loopers.application.brand.dto.BrandCreateCommand
import com.loopers.application.brand.dto.BrandInfo
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class AdminCatalogFacade(
    private val brandService: BrandService,
) {
    @Transactional(readOnly = true)
    fun getBrands(page: Int, size: Int): Page<BrandInfo> {
        return brandService.getBrands(page = page, size = size)
            .map(BrandInfo::from)
    }

    @Transactional(readOnly = true)
    fun getBrand(brandId: Long): BrandInfo {
        val brand = brandService.getBrand(brandId)
        brand.ensureDisplayable()

        return BrandInfo.from(brand)
    }

    @Transactional
    fun createBrand(command: BrandCreateCommand): BrandInfo {
        return brandService.createBrand(command)
            .let(BrandInfo::from)
    }
}
