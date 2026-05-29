package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductStatus
import org.springframework.data.domain.Limit
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Window
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByIdAndStatusNot(id: Long, status: ProductStatus): Product?

    fun findByBrandIdAndStatusNot(brandId: Long, status: ProductStatus): List<Product>

    fun findByStatusNot(
        status: ProductStatus,
        scrollPosition: ScrollPosition,
        limit: Limit,
        sort: Sort,
    ): Window<Product>

    fun findByBrandIdAndStatusNot(
        brandId: Long,
        status: ProductStatus,
        scrollPosition: ScrollPosition,
        limit: Limit,
        sort: Sort,
    ): Window<Product>
}
