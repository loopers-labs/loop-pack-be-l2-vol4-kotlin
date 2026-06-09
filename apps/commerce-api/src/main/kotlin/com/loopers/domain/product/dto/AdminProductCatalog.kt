package com.loopers.domain.product.dto

import com.loopers.domain.brand.Brand
import com.loopers.domain.inventory.Inventory
import com.loopers.domain.product.Product
import com.loopers.domain.productstat.ProductStat

data class AdminProductCatalog(
    val product: Product,
    val brand: Brand,
    val productStat: ProductStat,
    val inventory: Inventory,
)
