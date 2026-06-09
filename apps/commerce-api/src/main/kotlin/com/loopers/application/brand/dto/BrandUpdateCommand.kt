package com.loopers.application.brand.dto

data class BrandUpdateCommand(
    val name: String,
    val description: String,
    val logoImageUrl: String,
)
