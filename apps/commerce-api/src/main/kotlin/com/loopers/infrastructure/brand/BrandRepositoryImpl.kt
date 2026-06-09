package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: Brand): Brand {
        val entity = brand.id
            ?.let { brandJpaRepository.findByIdAndDeletedAtIsNull(it) }
            ?.also { it.updateFrom(brand) }
            ?: BrandJpaEntity.from(brand)

        return brandJpaRepository.save(entity).toDomain()
    }

    override fun find(id: Long): Brand? {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id)
            ?.toDomain()
    }

    override fun findAll(ids: Collection<Long>): List<Brand> {
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids)
            .map { it.toDomain() }
    }

    override fun delete(id: Long) {
        brandJpaRepository.findByIdAndDeletedAtIsNull(id)
            ?.delete()
    }
}
