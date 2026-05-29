package com.loopers.application.brand

import com.loopers.application.brand.dto.BrandInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandFacade(
    private val brandService: BrandService,
) {
    @Transactional(readOnly = true)
    fun getBrand(brandId: Long): BrandInfo {
        return brandService.getBrand(brandId)
            .let(BrandInfo::from)
    }
}
