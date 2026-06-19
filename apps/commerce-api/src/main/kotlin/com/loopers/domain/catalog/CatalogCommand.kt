package com.loopers.domain.catalog

class CatalogCommand {
    data class CreateBrand(
        val name: String,
    )

    data class UpdateBrand(
        val name: String,
    )

    data class CreateProduct(
        val brandId: Long,
        val name: String,
        val price: Long,
        val initialStock: Int,
        val detailImageUrls: List<String>,
    )

    data class UpdateProduct(
        val name: String,
        val price: Long,
        val detailImageUrls: List<String>,
    )

    data class ChangeStock(
        val productId: Long,
        val quantity: Int,
    )
}
