package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.shared.CursorPage
import org.springframework.data.domain.Limit
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: Brand): Brand =
        brandJpaRepository.save(brand)

    override fun findActiveById(id: Long): Brand? =
        brandJpaRepository.findByIdAndDeletedAtIsNull(id)

    override fun existsByName(name: BrandName): Boolean =
        brandJpaRepository.existsByNameValue(name.value)

    override fun existsByNameExcludingId(name: BrandName, id: Long): Boolean =
        brandJpaRepository.existsByNameValueAndIdNot(name.value, id)

    override fun findAll(cursor: Long?, size: Int): CursorPage<Brand> {
        val scrollPosition =
            if (cursor == null) {
                ScrollPosition.keyset()
            } else {
                ScrollPosition.of(mapOf<String, Any>("id" to cursor), ScrollPosition.Direction.FORWARD)
            }
        val window = brandJpaRepository.findByDeletedAtIsNull(
            scrollPosition,
            Limit.of(size),
            Sort.by(Sort.Direction.DESC, "id"),
        )
        return CursorPage(window.content, window.hasNext())
    }
}
