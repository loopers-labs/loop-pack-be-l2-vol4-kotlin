package com.loopers.application.product

import com.loopers.application.product.dto.ProductListCommand
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component

@Component
class ProductService(
    private val productRepository: ProductRepository,
) {
    fun getProduct(productId: Long): Product {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Product not found.")

        return product
    }

    fun getProducts(command: ProductListCommand): Page<ProductSummary> {
        return productRepository.findDisplayableSummaries(
            brandId = command.brandId,
            sort = command.sort,
            page = command.page,
            size = command.size,
        )
    }
}
