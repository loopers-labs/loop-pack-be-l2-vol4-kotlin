package com.loopers.application.shopping

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CartQueryFacade(
    private val cartApplicationService: CartApplicationService,
    private val cartCatalogPort: CartCatalogPort,
) {
    @Transactional(readOnly = true)
    fun getCart(userId: Long): CartInfo {
        val items = cartApplicationService.getItems(userId)
        val products = cartCatalogPort.getCartProducts(items.map { it.productId }).associateBy { it.productId }

        return CartInfo(
            userId = userId,
            items = items.map { item ->
                val product = products[item.productId]
                CartLineInfo(
                    productId = item.productId,
                    productName = product?.productName,
                    brandName = product?.brandName,
                    price = product?.price,
                    quantity = item.quantity,
                    stockQuantity = product?.stockQuantity,
                    orderable = product?.let { it.orderable && item.quantity <= it.stockQuantity } ?: false,
                )
            },
        )
    }
}
