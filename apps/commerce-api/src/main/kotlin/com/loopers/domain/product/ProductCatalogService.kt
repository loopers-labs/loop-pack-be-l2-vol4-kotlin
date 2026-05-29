package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.inventory.Inventory
import com.loopers.domain.product.dto.ProductCatalog
import com.loopers.domain.productstat.ProductStat
import org.springframework.stereotype.Component

@Component
class ProductCatalogService {
    fun display(
        product: Product,
        brand: Brand,
        productStat: ProductStat,
        inventory: Inventory? = null,
    ): ProductCatalog {
        product.ensureDisplayable()
        brand.ensureDisplayable()

        return ProductCatalog(
            product = product,
            brand = brand,
            productStat = productStat,
            inventory = inventory,
        )
    }
}
