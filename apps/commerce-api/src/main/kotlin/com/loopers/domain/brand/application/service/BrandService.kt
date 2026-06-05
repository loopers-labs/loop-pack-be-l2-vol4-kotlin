package com.loopers.domain.brand.application.service

import com.loopers.domain.brand.application.command.BrandRegisterCommand
import com.loopers.domain.brand.application.command.BrandUpdateCommand
import com.loopers.domain.brand.exception.BrandNotActiveException
import com.loopers.domain.brand.exception.InvalidBrandException
import com.loopers.domain.brand.model.BrandModel
import com.loopers.domain.brand.port.BrandRepository
import com.loopers.domain.brand.vo.BrandName
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {
    @Transactional
    fun register(command: BrandRegisterCommand): BrandModel =
        try {
            brandRepository.save(
                BrandModel(
                    name = BrandName.of(command.name),
                ),
            )
        } catch (e: InvalidBrandException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }

    @Transactional
    fun update(
        brandId: Long,
        command: BrandUpdateCommand,
    ): BrandModel {
        val brand = findById(brandId)
        return try {
            brandRepository.save(
                brand.rename(BrandName.of(command.name)),
            )
        } catch (e: InvalidBrandException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
    }

    @Transactional
    fun softDelete(brandId: Long): BrandModel {
        val brand = findById(brandId)
        return brandRepository.save(brand.delete())
    }

    @Transactional(readOnly = true)
    fun findById(brandId: Long): BrandModel {
        val brand = brandRepository.findById(brandId) ?: throwNotFound()
        try {
            brand.requireActive()
        } catch (e: BrandNotActiveException) {
            throw CoreException(ErrorType.NOT_FOUND, e.message, e)
        }
        return brand
    }

    @Transactional(readOnly = true)
    fun findByIds(brandIds: Set<Long>): List<BrandModel> {
        if (brandIds.isEmpty()) {
            return emptyList()
        }
        val brands = brandRepository.findAllByIds(brandIds)
        if (brands.size != brandIds.size) {
            throwNotFound()
        }
        try {
            brands.forEach { it.requireActive() }
        } catch (e: BrandNotActiveException) {
            throw CoreException(ErrorType.NOT_FOUND, e.message, e)
        }
        return brands
    }

    private fun throwNotFound(): Nothing {
        throw CoreException(ErrorType.NOT_FOUND)
    }
}
