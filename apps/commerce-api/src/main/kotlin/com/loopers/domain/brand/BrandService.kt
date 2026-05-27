package com.loopers.domain.brand

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandService(
    private val brandRepository: BrandRepository,
) {
    @Transactional
    fun create(command: CreateCommand): BrandModel {
        return brandRepository.save(
            BrandModel(
                name = command.name,
                description = command.description,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun getActive(brandId: Long): BrandModel {
        return brandRepository.findActiveById(brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
    }

    data class CreateCommand(
        val name: String,
        val description: String,
    )
}
