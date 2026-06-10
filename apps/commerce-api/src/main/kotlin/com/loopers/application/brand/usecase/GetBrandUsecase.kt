package com.loopers.application.brand.usecase

import com.loopers.application.brand.BrandInfo
import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetBrandUsecase(
    private val brandRepository: BrandRepository,
) {
    @Transactional(readOnly = true)
    fun execute(brandId: Long): BrandInfo {
        return brandRepository.findActiveById(brandId)
            ?.let { BrandInfo.from(it) }
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
    }
}
