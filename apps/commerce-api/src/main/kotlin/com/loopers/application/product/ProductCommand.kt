package com.loopers.application.product

data class CreateProductCommand(
    val name: String,
    val price: Long,
    val description: String,
    val brandId: Long,
    val quantity: Int,
)

data class UpdateProductCommand(
    val id: Long,
    val name: String,
    val price: Long,
    val description: String,
    val brandId: Long,
    val quantity: Int,
)
