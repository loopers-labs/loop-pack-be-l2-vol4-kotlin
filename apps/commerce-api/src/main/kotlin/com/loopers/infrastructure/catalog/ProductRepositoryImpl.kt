package com.loopers.infrastructure.catalog

import com.loopers.domain.catalog.Product
import com.loopers.domain.catalog.ProductRepository
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun save(product: Product): Product = productJpaRepository.save(product)

    override fun findById(productId: Long): Product? = productJpaRepository.findByIdAndDeletedAtIsNull(productId)

    override fun existsActiveNameInBrand(brandId: Long, name: String): Boolean =
        productJpaRepository.existsActiveNameInBrand(brandId, name)
}
