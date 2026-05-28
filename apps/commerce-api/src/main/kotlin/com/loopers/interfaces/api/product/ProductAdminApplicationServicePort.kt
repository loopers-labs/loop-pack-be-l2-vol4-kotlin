package com.loopers.interfaces.api.product

import com.loopers.application.product.CreateProductCommand
import com.loopers.application.product.ProductDetail
import com.loopers.application.product.ProductSummary
import com.loopers.application.product.UpdateProductCommand
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

interface ProductAdminApplicationServicePort {
    fun getProduct(id: Long): ProductDetail
    fun getProducts(brandId: Long?, pageRequest: PageRequest): PageResult<ProductSummary>
    fun createProduct(command: CreateProductCommand): ProductDetail
    fun updateProduct(command: UpdateProductCommand): ProductDetail
    fun deleteProduct(id: Long)
}
