package com.loopers.application.brand

data class CreateBrandCommand(
    val name: String,
    val description: String,
)

data class UpdateBrandCommand(
    val id: Long,
    val name: String,
    val description: String,
)
