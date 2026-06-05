package com.loopers.domain.product.application.service

import com.loopers.domain.product.application.command.ProductRegisterCommand
import com.loopers.domain.product.application.command.ProductSearchCommand
import com.loopers.domain.product.application.command.ProductUpdateCommand
import com.loopers.domain.product.application.info.ProductSnapshotInfo
import com.loopers.domain.product.exception.InvalidProductException
import com.loopers.domain.product.exception.ProductNotOrderableException
import com.loopers.domain.product.model.ProductModel
import com.loopers.domain.product.port.ProductRepository
import com.loopers.domain.product.port.ProductSearchCondition
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.ProductName
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductService(
    private val productRepository: ProductRepository,
) {
    @Transactional
    fun register(command: ProductRegisterCommand): ProductModel =
        try {
            productRepository.save(
                ProductModel(
                    brandId = command.brandId,
                    name = ProductName.of(command.name),
                    price = Money.of(command.price),
                ),
            )
        } catch (e: InvalidProductException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }

    @Transactional
    fun update(
        productId: Long,
        command: ProductUpdateCommand,
    ): ProductModel {
        val product = findById(productId)
        return try {
            productRepository.save(
                product
                    .changeName(ProductName.of(command.name))
                    .changePrice(Money.of(command.price)),
            )
        } catch (e: InvalidProductException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
    }

    @Transactional
    fun softDelete(productId: Long): ProductModel {
        val product = findById(productId)
        return productRepository.save(product.delete())
    }

    @Transactional
    fun softDeleteByBrandId(brandId: Long): List<ProductModel> {
        val deletedProducts = productRepository.findByBrandId(brandId)
            .map { it.delete() }
        return productRepository.saveAll(deletedProducts)
    }

    @Transactional(readOnly = true)
    fun findById(productId: Long): ProductModel {
        val product = productRepository.findById(productId) ?: throwNotFound()
        if (product.deletedAtOrNull != null) {
            throwNotFound()
        }
        return product
    }

    @Transactional(readOnly = true)
    fun findOrderableSnapshots(productIds: List<Long>): List<ProductSnapshotInfo> {
        val productsById = productRepository.findAllByIds(productIds).associateBy { it.id }
        return productIds.map { productId ->
            val product = productsById[productId] ?: throwNotFound()
            try {
                product.requireOrderable()
            } catch (e: ProductNotOrderableException) {
                throw CoreException(ErrorType.NOT_FOUND, e.message, e)
            }
            ProductSnapshotInfo(
                productId = product.id,
                productName = product.name.value,
                unitPrice = product.price.value,
            )
        }
    }

    @Transactional(readOnly = true)
    fun findProducts(command: ProductSearchCommand): List<ProductModel> {
        return productRepository.findByCondition(
            ProductSearchCondition(
                brandId = command.brandId,
                sort = command.sort,
                page = command.page,
                size = command.size,
            ),
        )
    }

    private fun throwNotFound(): Nothing {
        throw CoreException(ErrorType.NOT_FOUND)
    }
}
