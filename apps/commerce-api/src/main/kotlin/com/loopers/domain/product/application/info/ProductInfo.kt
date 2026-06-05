package com.loopers.domain.product.application.info

import com.loopers.domain.product.model.ProductModel
import java.time.ZonedDateTime

data class ProductInfo(
    val id: Long,
    val brandId: Long,
    val name: String,
    val price: Long,
    val deletedAtOrNull: ZonedDateTime?,
) {
    companion object {
        fun from(product: ProductModel): ProductInfo = ProductInfo(
            id = product.id,
            brandId = product.brandId,
            name = product.name.value,
            price = product.price.value,
            deletedAtOrNull = product.deletedAtOrNull,
        )
    }
}
