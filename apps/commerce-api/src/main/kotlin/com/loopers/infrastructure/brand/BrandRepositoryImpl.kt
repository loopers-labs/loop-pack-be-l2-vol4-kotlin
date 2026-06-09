package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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

    override fun findAllByIds(brandIds: Collection<Long>): List<Brand> {
        if (brandIds.isEmpty()) {
            return emptyList()
        }

        return brandJpaRepository.findAllById(brandIds)
            .map(BrandMapper::toDomain)
    }

    override fun findDisplayable(page: Int, size: Int): Page<Brand> {
        val pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"),
            ),
        )
        return brandJpaRepository.findAllByIsDeletedFalse(pageable)
            .map(BrandMapper::toDomain)
    }

    override fun existsByName(name: String): Boolean {
        return brandJpaRepository.existsByName(name)
    }

    override fun save(brand: Brand): Brand {
        return BrandMapper.toEntity(brand)
            .let(brandJpaRepository::save)
            .let(BrandMapper::toDomain)
    }

    override fun update(brand: Brand): Brand {
        val entity = brandJpaRepository.findByIdOrNull(brand.id)
            ?.also { it.update(brand) }
            ?: throw CoreException(ErrorType.NOT_FOUND, "Brand not found.")

        return brandJpaRepository.save(entity)
            .let(BrandMapper::toDomain)
    }
}
