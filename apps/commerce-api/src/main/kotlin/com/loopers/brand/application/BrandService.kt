package com.loopers.brand.application

import com.loopers.brand.domain.Brand
import com.loopers.brand.domain.BrandErrorCode
import com.loopers.brand.domain.BrandName
import com.loopers.brand.domain.BrandRepository
import com.loopers.brand.domain.BrandStatus
import com.loopers.shared.domain.CursorPage
import com.loopers.shared.domain.IdCursor
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandService(
    private val brandRepository: BrandRepository,
) {
    @Transactional
    fun register(command: BrandCreateCommand): BrandInfo {
        val name = BrandName(command.name)
        if (brandRepository.existsByName(name)) {
            throw ConflictException(BrandErrorCode.DUPLICATE_BRAND_NAME)
        }
        val brand = brandRepository.save(Brand(name, command.description))
        return BrandInfo.from(brand)
    }

    fun get(id: Long): BrandInfo {
        val brand = brandRepository.findActiveById(id)
            ?: throw NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)
        return BrandInfo.from(brand)
    }

    fun list(cursor: IdCursor?, size: Int): CursorPage<BrandInfo> {
        val page = brandRepository.findAll(cursor, size)
        return CursorPage(page.content.map(BrandInfo::from), page.hasNext, page.nextCursor)
    }

    @Transactional
    fun update(command: BrandUpdateCommand): BrandInfo {
        val brand = brandRepository.findActiveById(command.id)
            ?: throw NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)
        val name = BrandName(command.name)
        if (brandRepository.existsByNameExcludingId(name, command.id)) {
            throw ConflictException(BrandErrorCode.DUPLICATE_BRAND_NAME)
        }
        brand.update(name, command.description)
        return BrandInfo.from(brand)
    }

    @Transactional
    fun delete(id: Long) {
        val brand = brandRepository.findActiveById(id)
            ?: throw NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)
        brand.transitionTo(BrandStatus.DELETED)
        // 후속(Product 머지 후): 이 브랜드의 Product soft delete cascade — BrandFacade에서 ProductService와 조합 (04 플랜 기능 7)
    }
}

data class BrandCreateCommand(
    val name: String,
    val description: String? = null,
)

data class BrandUpdateCommand(
    val id: Long,
    val name: String,
    val description: String? = null,
)

data class BrandInfo(
    val id: Long,
    val name: String,
    val description: String?,
) {
    companion object {
        fun from(brand: Brand): BrandInfo =
            BrandInfo(
                id = brand.id,
                name = brand.name.value,
                description = brand.description,
            )
    }
}
