package com.loopers.domain.product.application.command

import com.loopers.domain.product.model.ProductSaleType

data class ProductRegisterCommand(
    val brandId: Long,
    val name: String,
    val price: Long,
    val initialStock: Long,
    val saleType: ProductSaleType = ProductSaleType.NORMAL,
)
