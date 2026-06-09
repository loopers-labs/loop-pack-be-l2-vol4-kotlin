package com.loopers.fixture.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.productstat.ProductStat

object ProductBrandFixture {
    fun createBrand(
        id: Long = 1L,
        name: String = "loopers",
        description: String = "loopers brand",
        logoImageUrl: String = "https://image.loopers/logo.png",
        isDeleted: Boolean = false,
    ): Brand {
        return Brand(
            id = id,
            name = name,
            description = description,
            logoImageUrl = logoImageUrl,
            isDeleted = isDeleted,
        )
    }

    fun createProduct(
        id: Long = 1L,
        brandId: Long = 1L,
        name: String = "loopers hoodie",
        price: Long = 10_000L,
        description: String = "loopers product",
        imageUrl: String = "https://image.loopers/product.png",
        isDeleted: Boolean = false,
    ): Product {
        return Product(
            id = id,
            brandId = brandId,
            name = name,
            price = price,
            description = description,
            imageUrl = imageUrl,
            isDeleted = isDeleted,
        )
    }

    fun createProductStat(
        id: Long = 1L,
        productId: Long = 1L,
        likeCount: Long = 0L,
    ): ProductStat {
        return ProductStat(
            id = id,
            productId = productId,
            likeCount = likeCount,
        )
    }
}
