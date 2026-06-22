package com.loopers.domain.product.service

import com.loopers.domain.brand.model.Brand
import com.loopers.domain.inventory.model.Inventory
import com.loopers.domain.product.dto.AdminProductCatalog
import com.loopers.domain.product.dto.ProductCatalog
import com.loopers.domain.product.model.Product
import com.loopers.domain.product.model.ProductStat
import org.springframework.stereotype.Component

@Component
class ProductCatalogService {
    fun display(
        product: Product,
        brand: Brand,
        productStat: ProductStat,
    ): ProductCatalog {
        return ProductCatalog(
            product = product,
            brand = brand,
            productStat = productStat,
        )
    }

    fun displayForAdmin(
        product: Product,
        brand: Brand,
        productStat: ProductStat,
        inventory: Inventory,
    ): AdminProductCatalog {
        return AdminProductCatalog(
            product = product,
            brand = brand,
            productStat = productStat,
            inventory = inventory,
        )
    }
}
