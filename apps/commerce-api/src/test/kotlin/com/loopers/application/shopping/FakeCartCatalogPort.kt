package com.loopers.application.shopping

class FakeCartCatalogPort : CartCatalogPort {
    private val products = mutableMapOf<Long, CartProductInfo>()

    fun register(
        productId: Long,
        productName: String = "product-$productId",
        brandName: String = "brand-$productId",
        price: Long = 1000L,
        stockQuantity: Int,
        orderable: Boolean = true,
    ) {
        products[productId] = CartProductInfo(
            productId = productId,
            productName = productName,
            brandName = brandName,
            price = price,
            stockQuantity = stockQuantity,
            orderable = orderable,
        )
    }

    override fun getCartProduct(productId: Long): CartProductInfo? = products[productId]

    override fun getCartProducts(productIds: Collection<Long>): List<CartProductInfo> =
        productIds.mapNotNull { products[it] }

    fun clear() {
        products.clear()
    }
}
