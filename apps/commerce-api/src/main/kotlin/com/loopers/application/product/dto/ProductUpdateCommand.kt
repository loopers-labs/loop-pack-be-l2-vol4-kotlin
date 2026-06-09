package com.loopers.application.product.dto

data class ProductUpdateCommand(
    val name: String,
    val price: Long,
    val description: String,
    val imageUrl: String,
)
