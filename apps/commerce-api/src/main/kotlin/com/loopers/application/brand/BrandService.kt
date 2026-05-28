package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {
    fun getBrand(brandId: Long): Brand {
        val brand = brandRepository.findById(brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Brand not found.")

        return brand
    }
}
