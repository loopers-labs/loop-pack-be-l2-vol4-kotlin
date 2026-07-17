package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSearchCondition
import com.loopers.support.paging.PageResult
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val productQueryRepository: ProductQueryRepository,
) : ProductRepository {
    override fun save(product: Product): Product {
        val entity = product.id
            ?.let { productJpaRepository.findByIdAndDeletedAtIsNull(it) }
            ?.also { it.updateFrom(product) }
            ?: ProductJpaEntity.from(product)

        return productJpaRepository.save(entity).toDomain()
    }

    override fun find(id: Long): Product? {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
            ?.toDomain()
    }

    override fun findAllByIds(ids: List<Long>): List<Product> {
        if (ids.isEmpty()) return emptyList()
        return productJpaRepository.findByIdInAndDeletedAtIsNull(ids)
            .map { it.toDomain() }
    }

    override fun findAll(condition: ProductSearchCondition): PageResult<Product> {
        return productQueryRepository.findAll(condition)
    }

    override fun delete(id: Long) {
        productJpaRepository.findByIdAndDeletedAtIsNull(id)
            ?.delete()
    }

    override fun findActiveIdsByBrandId(brandId: Long): List<Long> {
        return productJpaRepository.findByBrandIdAndDeletedAtIsNull(brandId)
            .mapNotNull { it.id }
    }
}
