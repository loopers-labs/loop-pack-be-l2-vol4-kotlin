package com.loopers.application.brand

import com.loopers.application.brand.dto.BrandCreateCommand
import com.loopers.application.brand.dto.BrandInfo
import com.loopers.application.brand.dto.BrandUpdateCommand
import com.loopers.application.product.ProductService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandFacade(
    private val brandService: BrandService,
    private val productService: ProductService,
) {
    @Transactional(readOnly = true)
    fun getBrands(page: Int, size: Int): Page<BrandInfo> {
        return brandService.getBrands(page = page, size = size)
            .map(BrandInfo::from)
    }

    @Transactional(readOnly = true)
    fun getBrand(brandId: Long): BrandInfo {
        return brandService.getBrand(brandId)
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

    @Transactional
    fun deleteBrand(brandId: Long) {
        brandService.deleteBrand(brandId)
        productService.deleteProductsByBrandId(brandId)
    }
}
