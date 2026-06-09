package com.loopers.application.product

import com.loopers.application.product.dto.ProductCreateCommand
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.application.product.dto.ProductUpdateCommand
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
) {
    fun getProduct(productId: Long): Product {
        val product = productRepository.findById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Product not found.")

        return product
    }

    fun getProducts(productIds: Collection<Long>): List<Product> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        return productRepository.findAllByIds(productIds)
    }

    fun getProducts(command: ProductListCommand): Page<ProductSummary> {
        return productRepository.findDisplayableSummaries(
            brandId = command.brandId,
            sort = command.sort,
            page = command.page,
            size = command.size,
        )
    }

    @Transactional
    fun createProduct(command: ProductCreateCommand): Product {
        if (productRepository.existsByBrandIdAndName(brandId = command.brandId, name = command.name)) {
            throw CoreException(ErrorType.CONFLICT, "Product name already exists in brand.")
        }

        return Product(
            brandId = command.brandId,
            name = command.name,
            price = command.price,
            description = command.description,
            imageUrl = command.imageUrl,
        ).let(productRepository::save)
    }

    @Transactional
    fun updateProduct(product: Product, command: ProductUpdateCommand): Product {
        if (
            product.name != command.name &&
            productRepository.existsByBrandIdAndNameAndIdNot(
                brandId = product.brandId,
                name = command.name,
                productId = product.id,
            )
        ) {
            throw CoreException(ErrorType.CONFLICT, "Product name already exists in brand.")
        }

        product.update(
            name = command.name,
            price = command.price,
            description = command.description,
            imageUrl = command.imageUrl,
        )

        return productRepository.update(product)
    }

    @Transactional
    fun deleteProduct(product: Product): Product {
        product.delete()

        return productRepository.update(product)
    }

    @Transactional
    fun deleteProductsByBrandId(brandId: Long): List<Product> {
        val products = productRepository.findAllByBrandId(brandId)
            .onEach(Product::delete)

        return productRepository.updateAll(products)
    }
}
