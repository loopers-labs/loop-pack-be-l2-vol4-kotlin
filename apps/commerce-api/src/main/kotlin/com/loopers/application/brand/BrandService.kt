package com.loopers.application.brand

import com.loopers.application.brand.dto.BrandCreateCommand
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
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

    fun getBrands(page: Int, size: Int): Page<Brand> {
        return brandRepository.findDisplayable(page = page, size = size)
    }

    fun createBrand(command: BrandCreateCommand): Brand {
        if (brandRepository.existsByName(command.name)) {
            throw CoreException(ErrorType.CONFLICT, "Brand name already exists.")
        }

        return Brand(
            name = command.name,
            description = command.description,
            logoImageUrl = command.logoImageUrl,
        ).let(brandRepository::save)
    }
}
