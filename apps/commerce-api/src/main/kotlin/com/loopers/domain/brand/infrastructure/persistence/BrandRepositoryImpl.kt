package com.loopers.domain.brand.infrastructure.persistence

import com.loopers.domain.brand.exception.BrandNotFoundException
import com.loopers.domain.brand.model.BrandModel
import com.loopers.domain.brand.port.BrandRepository
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: BrandModel): BrandModel {
        val entity = if (brand.id == 0L) {
            BrandJpaEntity.fromDomain(brand)
        } else {
            brandJpaRepository.findById(brand.id).orElseThrow { BrandNotFoundException(brand.id) }
                .also { it.updateFrom(brand) }
        }
        return brandJpaRepository.saveAndFlush(entity).toDomain()
    }

    override fun findById(brandId: Long): BrandModel? =
        brandJpaRepository.findById(brandId).map { it.toDomain() }.orElse(null)

    override fun findAllByIds(brandIds: Collection<Long>): List<BrandModel> =
        brandJpaRepository.findAllById(brandIds).map { it.toDomain() }
}
