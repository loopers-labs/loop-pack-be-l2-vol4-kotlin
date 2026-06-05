package com.loopers.domain.product.application.command

data class ProductRegisterCommand(
    val brandId: Long,
    val name: String,
    val price: Long,
    val initialStock: Long,
)
