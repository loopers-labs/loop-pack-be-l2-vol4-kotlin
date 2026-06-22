package com.loopers.domain.product.dto

import com.loopers.domain.brand.model.Brand
import com.loopers.domain.inventory.model.Inventory
import com.loopers.domain.product.model.Product
import com.loopers.domain.product.model.ProductStat

data class AdminProductCatalog(
    val product: Product,
    val brand: Brand,
    val productStat: ProductStat,
    val inventory: Inventory,
)
