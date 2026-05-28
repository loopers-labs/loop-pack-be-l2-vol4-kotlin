package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun findById(brandId: Long): Brand? {
        return brandJpaRepository.findByIdOrNull(brandId)
            ?.let(BrandMapper::toDomain)
    }

    override fun existsByName(name: String): Boolean {
        return brandJpaRepository.existsByName(name)
    }

    override fun save(brand: Brand): Brand {
        return BrandMapper.toEntity(brand)
            .let(brandJpaRepository::save)
            .let(BrandMapper::toDomain)
    }
}
