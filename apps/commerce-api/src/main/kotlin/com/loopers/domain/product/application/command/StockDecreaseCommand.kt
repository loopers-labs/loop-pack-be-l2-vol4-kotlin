package com.loopers.domain.product.application.command

data class StockDecreaseCommand(
    val productId: Long,
    val quantity: Long,
)
