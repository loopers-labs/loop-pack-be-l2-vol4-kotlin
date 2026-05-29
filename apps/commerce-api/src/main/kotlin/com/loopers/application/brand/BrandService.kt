package com.loopers.application.brand

import com.loopers.application.brand.dto.BrandCreateCommand
import com.loopers.application.brand.dto.BrandUpdateCommand
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {
    @Transactional(readOnly = true)
    fun getBrand(brandId: Long): Brand {
        val brand = brandRepository.findById(brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Brand not found.")

        return brand
    }

    @Transactional(readOnly = true)
    fun getDisplayableBrand(brandId: Long): Brand {
        val brand = getBrand(brandId)
        brand.ensureDisplayable()

        return brand
    }

    @Transactional(readOnly = true)
    fun getBrands(page: Int, size: Int): Page<Brand> {
        return brandRepository.findDisplayable(page = page, size = size)
    }

    @Transactional
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

    @Transactional
    fun updateBrand(brandId: Long, command: BrandUpdateCommand): Brand {
        val brand = getDisplayableBrand(brandId)

        if (brand.name != command.name && brandRepository.existsByName(command.name)) {
            throw CoreException(ErrorType.CONFLICT, "Brand name already exists.")
        }

        brand.update(
            name = command.name,
            description = command.description,
            logoImageUrl = command.logoImageUrl,
        )

        return brandRepository.update(brand)
    }

    @Transactional
    fun deleteBrand(brandId: Long): Brand {
        val brand = getDisplayableBrand(brandId)
        brand.delete()

        return brandRepository.update(brand)
    }
}
