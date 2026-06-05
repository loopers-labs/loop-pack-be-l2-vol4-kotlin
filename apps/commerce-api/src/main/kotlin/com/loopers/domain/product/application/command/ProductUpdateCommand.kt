package com.loopers.domain.product.application.command

data class ProductUpdateCommand(
    val name: String,
    val price: Long,
)
