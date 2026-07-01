package com.loopers.domain.product.application.service

import com.loopers.domain.product.application.command.StockDecreaseCommand
import com.loopers.domain.product.exception.InsufficientStockException
import com.loopers.domain.product.exception.InvalidProductException
import com.loopers.domain.product.model.StockModel
import com.loopers.domain.product.port.StockRepository
import com.loopers.domain.product.vo.Quantity
import com.loopers.domain.product.vo.StockQuantity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class StockService(
    private val stockRepository: StockRepository,
) {
    @Transactional
    fun initialize(
        productId: Long,
        leftStock: Long,
    ): StockModel =
        try {
            stockRepository.save(
                StockModel.initialize(
                    productId = productId,
                    leftStock = StockQuantity.of(leftStock),
                ),
            )
        } catch (e: InvalidProductException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }

    @Transactional
    fun decreaseAll(commands: List<StockDecreaseCommand>): List<StockModel> {
        val requestedQuantities = aggregateQuantities(commands)
        if (requestedQuantities.isEmpty()) {
            return emptyList()
        }

        val productIds = requestedQuantities.keys.sorted()
        val stocksByProductId = stockRepository
            .findByProductIdsForUpdate(productIds)
            .associateBy { it.productId }
        if (stocksByProductId.size != requestedQuantities.size) {
            throw CoreException(ErrorType.NOT_FOUND)
        }

        val decreasedStocks = try {
            productIds.map { productId ->
                val quantity = requestedQuantities[productId] ?: throw CoreException(ErrorType.NOT_FOUND)
                val stock = stocksByProductId[productId] ?: throw CoreException(ErrorType.NOT_FOUND)
                stock.decrease(quantity)
            }
        } catch (e: InsufficientStockException) {
            throw CoreException(ErrorType.CONFLICT, e.message, e)
        }
        return stockRepository.saveAll(decreasedStocks)
    }

    @Transactional
    fun restoreAll(commands: List<StockDecreaseCommand>): List<StockModel> {
        val requestedQuantities = aggregateQuantities(commands)
        if (requestedQuantities.isEmpty()) {
            return emptyList()
        }

        val productIds = requestedQuantities.keys.sorted()
        val stocksByProductId = stockRepository
            .findByProductIdsForUpdate(productIds)
            .associateBy { it.productId }
        if (stocksByProductId.size != requestedQuantities.size) {
            throw CoreException(ErrorType.NOT_FOUND)
        }

        val restoredStocks = productIds.map { productId ->
            val quantity = requestedQuantities[productId] ?: throw CoreException(ErrorType.NOT_FOUND)
            val stock = stocksByProductId[productId] ?: throw CoreException(ErrorType.NOT_FOUND)
            stock.increase(quantity)
        }
        return stockRepository.saveAll(restoredStocks)
    }

    private fun aggregateQuantities(commands: List<StockDecreaseCommand>): Map<Long, Quantity> =
        try {
            commands
                .groupBy { it.productId }
                .mapValues { (_, commandsByProduct) ->
                    Quantity.of(commandsByProduct.sumOf { Quantity.of(it.quantity).value })
                }
        } catch (e: InvalidProductException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
}
