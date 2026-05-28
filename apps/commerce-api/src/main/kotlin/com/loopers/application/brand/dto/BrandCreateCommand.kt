package com.loopers.application.brand.dto

data class BrandCreateCommand(
    val name: String,
    val description: String,
    val logoImageUrl: String,
)
