package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSearchCondition
import com.loopers.domain.product.StockQuantity
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
            ?.also { it.apply(product) }
            ?: ProductJpaEntity.from(product)

        return productJpaRepository.save(entity).toDomain()
    }

    override fun find(id: Long): Product? {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id)
            ?.toDomain()
    }

    override fun findAll(condition: ProductSearchCondition): PageResult<Product> {
        return productQueryRepository.findAll(condition)
    }

    override fun deductStockIfEnough(id: Long, quantity: StockQuantity): Boolean {
        return productJpaRepository.deductStockIfEnough(id = id, quantity = quantity.value) == 1
    }

    override fun restoreStock(id: Long, quantity: StockQuantity): Boolean {
        return productJpaRepository.restoreStock(id = id, quantity = quantity.value) == 1
    }

    override fun increaseLikeCount(id: Long): Boolean {
        return productJpaRepository.increaseLikeCount(id) == 1
    }

    override fun decreaseLikeCount(id: Long): Boolean {
        return productJpaRepository.decreaseLikeCount(id) == 1
    }

    override fun delete(id: Long) {
        productJpaRepository.findByIdAndDeletedAtIsNull(id)
            ?.delete()
    }
}
