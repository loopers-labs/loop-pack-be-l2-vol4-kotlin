package com.loopers.application.admin.product

import com.loopers.application.product.ProductService
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.domain.product.dto.ProductSummary
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class AdminProductFacade(
    private val productService: ProductService,
) {
    fun getProducts(command: ProductListCommand): Page<ProductSummary> {
        return productService.getProducts(command)
    }
}
