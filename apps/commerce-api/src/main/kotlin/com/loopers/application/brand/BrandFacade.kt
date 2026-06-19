package com.loopers.application.brand

import com.loopers.application.product.ProductApplicationService
import com.loopers.application.product.ProductFacade
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class BrandFacade(
    private val brandApplicationService: BrandApplicationService,
    private val productApplicationService: ProductApplicationService,
    private val productFacade: ProductFacade,
) {
    @Transactional
    fun createBrand(
        name: String,
        description: String,
        logoImageUrl: String?,
    ): BrandInfo {
        return brandApplicationService.createBrand(name, description, logoImageUrl)
    }

    fun getBrand(brandId: Long): BrandInfo {
        return brandApplicationService.getBrand(brandId)
    }

    @Transactional
    fun updateBrand(
        brandId: Long,
        name: String,
        description: String,
        logoImageUrl: String?,
    ): BrandInfo {
        return brandApplicationService.updateBrand(brandId, name, description, logoImageUrl)
    }

    @Transactional
    fun deleteBrand(brandId: Long) {
        val productIds = productApplicationService.findActiveIdsByBrandId(brandId)
        productIds.forEach { productId ->
            productFacade.deleteProduct(productId)
        }
        brandApplicationService.deleteBrand(brandId)
    }
}
