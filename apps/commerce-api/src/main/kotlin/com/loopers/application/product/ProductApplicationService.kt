package com.loopers.application.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductPrice
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSearchCondition
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.paging.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class ProductApplicationService(
    private val productRepository: ProductRepository,
) {
    @Transactional
    fun createProduct(
        brandId: Long,
        name: String,
        description: String,
        price: ProductPrice,
    ): Product {
        return productRepository.save(
            Product(
                brandId = brandId,
                name = name,
                description = description,
                price = price,
            ),
        )
    }

    fun getProduct(id: Long): Product {
        return productRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다. id=$id")
    }

    fun getProducts(condition: ProductSearchCondition): PageResult<Product> {
        return productRepository.findAll(condition)
    }

    @Transactional
    fun updateProduct(
        id: Long,
        name: String,
        description: String,
        price: ProductPrice,
    ): Product {
        val product = getProduct(id)
        product.rename(name)
        product.changeDescription(description)
        product.changePrice(price)
        return productRepository.save(product)
    }

    @Transactional
    fun deleteProduct(id: Long) {
        getProduct(id)
        productRepository.delete(id)
    }

    @Transactional
    fun increaseLikeCount(id: Long): Product {
        if (!productRepository.increaseLikeCount(id)) {
            throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다. id=$id")
        }
        return getProduct(id)
    }

    @Transactional
    fun decreaseLikeCount(id: Long): Product {
        val product = getProduct(id)
        product.validateLikeCountDecreasable()

        if (!productRepository.decreaseLikeCount(id)) {
            throw CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 음수가 될 수 없습니다.")
        }
        return getProduct(id)
    }
}
