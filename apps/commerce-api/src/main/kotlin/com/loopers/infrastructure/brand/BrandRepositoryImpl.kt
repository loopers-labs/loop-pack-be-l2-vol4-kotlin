package com.loopers.infrastructure.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandName
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.brand.BrandStatus
import com.loopers.domain.shared.CursorPage
import com.loopers.domain.shared.IdCursor
import org.springframework.data.domain.Limit
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository

@Repository
class BrandRepositoryImpl(
    private val brandJpaRepository: BrandJpaRepository,
) : BrandRepository {
    override fun save(brand: Brand): Brand =
        brandJpaRepository.save(brand)

    override fun findActiveById(id: Long): Brand? =
        brandJpaRepository.findByIdAndStatusNot(id, BrandStatus.DELETED)

    override fun existsByName(name: BrandName): Boolean =
        brandJpaRepository.existsByNameValue(name.value)

    override fun existsByNameExcludingId(name: BrandName, id: Long): Boolean =
        brandJpaRepository.existsByNameValueAndIdNot(name.value, id)

    override fun findAll(cursor: IdCursor?, size: Int): CursorPage<Brand> {
        val scrollPosition =
            if (cursor == null) {
                ScrollPosition.keyset()
            } else {
                ScrollPosition.of(mapOf<String, Any>("id" to cursor.id), ScrollPosition.Direction.FORWARD)
            }
        val window = brandJpaRepository.findByStatusNot(
            BrandStatus.DELETED,
            scrollPosition,
            Limit.of(size),
            Sort.by(Sort.Direction.DESC, "id"),
        )
        val nextCursor =
            if (window.hasNext() && window.content.isNotEmpty()) {
                IdCursor(window.content.last().id)
            } else {
                null
            }
        return CursorPage(window.content, window.hasNext(), nextCursor)
    }
}
