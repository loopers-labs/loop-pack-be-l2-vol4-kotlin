package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.PageRequest as SpringPageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class BrandRepositoryAdapter(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepositoryPort {
    override fun findByIdOrNull(id: Long): Brand? =
        brandJpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun findAllByIds(ids: List<Long>): List<Brand> {
        if (ids.isEmpty()) return emptyList()
        return brandJpaRepository.findAllById(ids).map { it.toDomain() }
    }

    override fun findAll(pageRequest: PageRequest): PageResult<Brand> {
        val springPageable = SpringPageRequest.of(pageRequest.page, pageRequest.size, Sort.by(Sort.Direction.ASC, "id"))
        val page = brandJpaRepository.findAll(springPageable)
        return PageResult.of(
            items = page.content.map { it.toDomain() },
            pageRequest = pageRequest,
            totalElements = page.totalElements,
        )
    }

    override fun existsByName(name: String): Boolean =
        brandJpaRepository.existsByName(name)

    override fun existsByNameAndIdNot(name: String, excludeId: Long): Boolean =
        brandJpaRepository.existsByNameAndIdNot(name, excludeId)

    override fun save(brand: Brand): Brand {
        val entity = if (brand.id == 0L) {
            BrandEntity.from(brand)
        } else {
            brandJpaRepository.findById(brand.id)
                .orElseThrow { CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.") }
                .apply { update(name = brand.name, description = brand.description) }
        }
        return brandJpaRepository.save(entity).toDomain()
    }

    override fun delete(brand: Brand) {
        if (brand.id == 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "ID가 없는 브랜드는 삭제할 수 없습니다.")
        }
        val entity = brandJpaRepository.findById(brand.id)
            .orElseThrow { CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.") }
        entity.delete()
        brandJpaRepository.save(entity)
    }
}
