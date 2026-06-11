package com.loopers.domain.brand

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class BrandService(
    private val brandRepositoryPort: BrandRepositoryPort,
) {
    fun getById(id: Long): Brand =
        brandRepositoryPort.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")

    fun findAllByIds(ids: List<Long>): List<Brand> = brandRepositoryPort.findAllByIds(ids)

    fun getAll(pageRequest: PageRequest): PageResult<Brand> = brandRepositoryPort.findAll(pageRequest)

    fun create(name: String, description: String): Brand {
        if (brandRepositoryPort.existsByName(name)) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.")
        }
        return brandRepositoryPort.save(Brand.create(name = name, description = description))
    }

    fun update(id: Long, name: String, description: String): Brand {
        val existing = getById(id)
        if (brandRepositoryPort.existsByNameAndIdNot(name, id)) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드 이름입니다.")
        }
        return brandRepositoryPort.save(existing.update(name = name, description = description))
    }

    fun delete(id: Long) {
        val existing = getById(id)
        brandRepositoryPort.delete(existing)
    }
}
