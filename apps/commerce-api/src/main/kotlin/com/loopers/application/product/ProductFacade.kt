package com.loopers.application.product

import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.product.LikeCountQueryPort
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.domain.stock.Stock
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductFacade(
    private val productRepositoryPort: ProductRepositoryPort,
    private val stockRepositoryPort: StockRepositoryPort,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val likeCountQueryPort: LikeCountQueryPort,
) {
    @Transactional(readOnly = true)
    fun getProduct(id: Long): ProductDetail {
        val product = productRepositoryPort.findByIdOrNull(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        val brand = brandRepositoryPort.findByIdOrNull(product.brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
        val stock = stockRepositoryPort.findByProductId(product.id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.")
        val likeCount = likeCountQueryPort.countByProductId(product.id)
        return ProductDetail.of(product, brand, stock, likeCount)
    }

    @Transactional(readOnly = true)
    fun getProducts(brandId: Long?, pageRequest: PageRequest): PageResult<ProductSummary> {
        val products = if (brandId == null) {
            productRepositoryPort.findAll(pageRequest)
        } else {
            productRepositoryPort.findAllByBrandId(brandId, pageRequest)
        }
        val summaries = products.items.map { product ->
            val brand = brandRepositoryPort.findByIdOrNull(product.brandId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
            val stock = stockRepositoryPort.findByProductId(product.id)
                ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.")
            ProductSummary.of(product, brand, stock)
        }
        return PageResult(
            items = summaries,
            page = products.page,
            size = products.size,
            totalElements = products.totalElements,
            totalPages = products.totalPages,
        )
    }

    @Transactional
    fun createProduct(command: CreateProductCommand): ProductDetail {
        val brand = brandRepositoryPort.findByIdOrNull(command.brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
        val product = productRepositoryPort.save(
            Product.create(
                name = command.name,
                price = command.price,
                description = command.description,
                brandId = command.brandId,
            ),
        )
        val stock = stockRepositoryPort.save(Stock.create(productId = product.id, quantity = command.quantity))
        return ProductDetail.of(product, brand, stock, likeCount = 0L)
    }

    @Transactional
    fun updateProduct(command: UpdateProductCommand): ProductDetail {
        val existing = productRepositoryPort.findByIdOrNull(command.id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        if (existing.brandId != command.brandId) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품의 브랜드는 변경할 수 없습니다.")
        }
        val updatedProduct = productRepositoryPort.save(
            existing.update(name = command.name, price = command.price, description = command.description),
        )
        val existingStock = stockRepositoryPort.findByProductId(command.id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.")
        val updatedStock = stockRepositoryPort.save(existingStock.updateQuantity(command.quantity))
        val brand = brandRepositoryPort.findByIdOrNull(updatedProduct.brandId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다.")
        val likeCount = likeCountQueryPort.countByProductId(updatedProduct.id)
        return ProductDetail.of(updatedProduct, brand, updatedStock, likeCount)
    }

    @Transactional
    fun deleteProduct(id: Long) {
        val product = productRepositoryPort.findByIdOrNull(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        val stock = stockRepositoryPort.findByProductId(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.")
        productRepositoryPort.delete(product)
        stockRepositoryPort.delete(stock)
    }

    /**
     * Brand cascade 보강용: 해당 브랜드의 모든 상품/재고를 soft delete.
     * Brand 트랜잭션과 같이 묶이도록 BrandFacade에서 호출.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    fun deleteAllByBrandId(brandId: Long) {
        val products = productRepositoryPort.findAllByBrandId(brandId)
        products.forEach { product ->
            val stock = stockRepositoryPort.findByProductId(product.id)
            productRepositoryPort.delete(product)
            stock?.let { stockRepositoryPort.delete(it) }
        }
    }
}
