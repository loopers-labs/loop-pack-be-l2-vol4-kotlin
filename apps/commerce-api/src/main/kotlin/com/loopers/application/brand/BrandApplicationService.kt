package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandApplicationService(
    private val brandRepository: BrandRepository,
) {
    @Transactional
    fun createBrand(
        name: String,
        description: String,
        logoImageUrl: String?,
    ): BrandInfo {
        return brandRepository.save(
            Brand(
                name = name,
                description = description,
                logoImageUrl = logoImageUrl,
            ),
        ).let { BrandInfo.from(it) }
    }

    @Transactional(readOnly = true)
    fun getBrand(id: Long): BrandInfo {
        return BrandInfo.from(findBrand(id))
    }

    @Transactional(readOnly = true)
    fun getBrands(ids: Collection<Long>): List<BrandInfo> {
        if (ids.isEmpty()) return emptyList()
        return brandRepository.findAll(ids).map { BrandInfo.from(it) }
    }

    @Transactional
    fun updateBrand(
        id: Long,
        name: String,
        description: String,
        logoImageUrl: String?,
    ): BrandInfo {
        val brand = findBrand(id)
        brand.rename(name)
        brand.changeDescription(description)
        brand.changeLogoImageUrl(logoImageUrl)
        return brandRepository.save(brand).let { BrandInfo.from(it) }
    }

    @Transactional
    fun deleteBrand(id: Long) {
        findBrand(id)
        brandRepository.delete(id)
    }

    private fun findBrand(id: Long): Brand {
        return brandRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다. id=$id")
    }
}
