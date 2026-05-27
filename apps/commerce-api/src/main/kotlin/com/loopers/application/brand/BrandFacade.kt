package com.loopers.application.brand

import com.loopers.domain.brand.BrandService
import org.springframework.stereotype.Component

@Component
class BrandFacade(
    private val brandService: BrandService,
) {
    fun getBrand(brandId: Long): BrandInfo {
        return brandService.getActive(brandId).let { BrandInfo.from(it) }
    }
}
