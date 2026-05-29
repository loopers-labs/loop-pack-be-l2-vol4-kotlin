package com.loopers.application.product.dto

data class ProductCreateCommand(
    val brandId: Long,
    val name: String,
    val price: Long,
    val description: String,
    val imageUrl: String,
)
