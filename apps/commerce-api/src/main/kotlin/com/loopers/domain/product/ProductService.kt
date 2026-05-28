package com.loopers.domain.product

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.stock.Stock
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class ProductService(
    private val productRepositoryPort: ProductRepositoryPort,
    private val stockRepositoryPort: StockRepositoryPort,
) {
    fun getById(id: Long): Product =
        productRepositoryPort.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")

    fun findAllByIds(ids: List<Long>): List<Product> = productRepositoryPort.findAllByIds(ids)

    fun findAllByBrandId(brandId: Long): List<Product> = productRepositoryPort.findAllByBrandId(brandId)

    fun getAll(brandId: Long?, sort: ProductSort, pageRequest: PageRequest): PageResult<Product> =
        productRepositoryPort.findAll(brandId, sort, pageRequest)

    fun getStockByProductId(productId: Long): Stock =
        stockRepositoryPort.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "재고를 찾을 수 없습니다.")

    fun create(name: String, price: Long, description: String, brandId: Long, quantity: Int): Pair<Product, Stock> {
        val product = productRepositoryPort.save(
            Product.create(name = name, price = price, description = description, brandId = brandId),
        )
        val stock = stockRepositoryPort.save(Stock.create(productId = product.id, quantity = quantity))
        return product to stock
    }

    fun update(
        id: Long,
        name: String,
        price: Long,
        description: String,
        brandId: Long,
        quantity: Int,
    ): Pair<Product, Stock> {
        val existing = getById(id)
        if (existing.brandId != brandId) {
            throw CoreException(ErrorType.BAD_REQUEST, "상품의 브랜드는 변경할 수 없습니다.")
        }
        val updatedProduct = productRepositoryPort.save(
            existing.update(name = name, price = price, description = description),
        )
        val existingStock = getStockByProductId(id)
        val updatedStock = stockRepositoryPort.save(existingStock.updateQuantity(quantity))
        return updatedProduct to updatedStock
    }

    fun delete(id: Long) {
        val product = getById(id)
        val stock = getStockByProductId(id)
        productRepositoryPort.delete(product)
        stockRepositoryPort.delete(stock)
    }

    fun deleteAllByBrandId(brandId: Long): List<Long> {
        val products = productRepositoryPort.findAllByBrandId(brandId)
        products.forEach { product ->
            val stock = stockRepositoryPort.findByProductId(product.id)
            productRepositoryPort.delete(product)
            stock?.let { stockRepositoryPort.delete(it) }
        }
        return products.map { it.id }
    }
}
