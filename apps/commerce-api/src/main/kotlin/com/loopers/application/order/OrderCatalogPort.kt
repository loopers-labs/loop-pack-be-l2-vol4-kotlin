package com.loopers.application.order

interface OrderCatalogPort {
    fun getOrderProducts(productIds: Collection<Long>): List<OrderCatalogProductInfo>
}

data class OrderCatalogProductInfo(
    val productId: Long,
    val productName: String,
    val brandName: String,
    val price: Long,
    val orderable: Boolean,
)
