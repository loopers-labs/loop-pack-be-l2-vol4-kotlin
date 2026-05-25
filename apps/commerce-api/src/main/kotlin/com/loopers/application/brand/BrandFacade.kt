package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandFacade(
    private val brandRepositoryPort: BrandRepositoryPort,
) {
    @Transactional(readOnly = true)
    fun getBrand(id: Long): Brand =
        brandRepositoryPort.findByIdOrNull(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")

    @Transactional(readOnly = true)
    fun getBrands(pageRequest: PageRequest): PageResult<Brand> =
        brandRepositoryPort.findAll(pageRequest)

    @Transactional
    fun createBrand(command: CreateBrandCommand): Brand {
        if (brandRepositoryPort.existsByName(command.name)) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.")
        }
        return brandRepositoryPort.save(Brand.create(name = command.name, description = command.description))
    }

    @Transactional
    fun updateBrand(command: UpdateBrandCommand): Brand {
        val existing = brandRepositoryPort.findByIdOrNull(command.id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
        if (brandRepositoryPort.existsByNameAndIdNot(command.name, command.id)) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.")
        }
        val updated = existing.update(name = command.name, description = command.description)
        return brandRepositoryPort.save(updated)
    }

    @Transactional
    fun deleteBrand(id: Long) {
        val existing = brandRepositoryPort.findByIdOrNull(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
        brandRepositoryPort.delete(existing)
    }
}
