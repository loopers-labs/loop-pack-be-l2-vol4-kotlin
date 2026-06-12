package com.loopers.domain.product.model

import com.loopers.domain.product.exception.InsufficientStockException
import com.loopers.domain.product.vo.Quantity
import com.loopers.domain.product.vo.StockQuantity

data class StockModel(
    val productId: Long,
    val leftStock: StockQuantity,
) {
    fun decrease(quantity: Quantity): StockModel {
        if (!hasEnough(quantity)) {
            throw InsufficientStockException(
                productId = productId,
                requested = quantity.value,
                available = leftStock.value,
            )
        }
        return copy(leftStock = leftStock.decrease(quantity))
    }

    fun hasEnough(quantity: Quantity): Boolean = leftStock.value >= quantity.value

    companion object {
        fun initialize(
            productId: Long,
            leftStock: StockQuantity,
        ): StockModel = StockModel(
            productId = productId,
            leftStock = leftStock,
        )
    }
}
