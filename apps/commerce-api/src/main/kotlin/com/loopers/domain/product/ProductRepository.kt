package com.loopers.domain.product

import com.loopers.domain.product.dto.ProductSummary
import org.springframework.data.domain.Page

interface ProductRepository {
    fun findById(productId: Long): Product?

    fun findAllByBrandId(brandId: Long): List<Product>

    fun findDisplayableSummaries(
        brandId: Long?,
        sort: ProductSort,
        page: Int,
        size: Int,
    ): Page<ProductSummary>

    fun existsByBrandIdAndName(brandId: Long, name: String): Boolean

    fun existsByBrandIdAndNameAndIdNot(brandId: Long, name: String, productId: Long): Boolean

    fun save(product: Product): Product

    fun update(product: Product): Product

    fun updateAll(products: Collection<Product>): List<Product>
}
