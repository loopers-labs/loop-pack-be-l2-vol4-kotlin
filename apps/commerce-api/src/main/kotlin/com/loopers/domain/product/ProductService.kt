package com.loopers.domain.product

import com.loopers.domain.brand.BrandRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class ProductService(
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val brandRepository: BrandRepository,
) {
    @Transactional
    fun create(command: CreateCommand): ProductModel {
        if (!brandRepository.existsActiveById(command.brandId)) {
            throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
        }

        val product = productRepository.save(
            ProductModel(
                brandId = command.brandId,
                name = command.name,
                description = command.description,
                price = command.price,
                stockQuantity = command.stockQuantity,
            ),
        )
        productStockRepository.save(ProductStockModel(productId = product.id, quantity = command.stockQuantity))
        return product
    }

    @Transactional
    fun incrementLikeCount(productId: Long) {
        val product = productRepository.findActiveById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        product.incrementLikeCount()
    }

    @Transactional
    fun decrementLikeCount(productId: Long) {
        val product = productRepository.findActiveById(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        product.decrementLikeCount()
    }

    data class CreateCommand(
        val brandId: Long,
        val name: String,
        val description: String,
        val price: BigDecimal,
        val stockQuantity: Int,
    )
}
