package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun findById(brandId: Long): com.loopers.domain.brand.Brand? {
        return brandJpaRepository.findByIdOrNull(brandId)
            ?.let(BrandMapper::toDomain)
    }

    override fun save(brand: com.loopers.domain.brand.Brand): com.loopers.domain.brand.Brand {
        val entity = if (brand.id == 0L) {
            BrandMapper.toEntity(brand)
        } else {
            brandJpaRepository.findByIdOrNull(brand.id)
                ?.also { it.update(brand) }
                ?: throw CoreException(ErrorType.NOT_FOUND, "Brand not found.")
        }

        return brandJpaRepository.save(entity)
            .let(BrandMapper::toDomain)
    }
}
