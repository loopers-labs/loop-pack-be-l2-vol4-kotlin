package com.loopers.application.shopping

interface CartCatalogPort {
    fun getCartProduct(productId: Long): CartProductInfo?

    fun getCartProducts(productIds: Collection<Long>): List<CartProductInfo>
}
