package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandErrorCode
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.shared.CursorPage
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

    @Transactional(readOnly = true)
    fun get(id: Long): BrandInfo {
        val brand = brandRepository.findActiveById(id)
            ?: throw NotFoundException(BrandErrorCode.BRAND_NOT_FOUND)
        return BrandInfo.from(brand)
    }

    @Transactional(readOnly = true)
    fun list(cursor: Long?, size: Int): CursorPage<BrandInfo> {
        val page = brandRepository.findAll(cursor, size)
        return CursorPage(page.content.map(BrandInfo::from), page.hasNext)
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
