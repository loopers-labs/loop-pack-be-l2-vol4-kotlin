package com.loopers.domain.product

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface ProductRepository {
    fun save(product: ProductModel): ProductModel
    fun findActiveById(id: Long): ProductModel?
    fun findActiveAllByIds(ids: List<Long>): List<ProductModel>
    fun findActiveAll(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<ProductModel>
    fun existsActiveById(id: Long): Boolean
    fun incrementLikeCount(productId: Long)
    fun decrementLikeCount(productId: Long)
}
