package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.Brand
import com.loopers.domain.catalog.BrandRepository
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: Brand): Brand = brandJpaRepository.save(brand)

    override fun findById(brandId: Long): Brand? = brandJpaRepository.findByIdAndDeletedAtIsNull(brandId)

    override fun existsActiveName(name: String): Boolean = brandJpaRepository.existsActiveName(name)
}
