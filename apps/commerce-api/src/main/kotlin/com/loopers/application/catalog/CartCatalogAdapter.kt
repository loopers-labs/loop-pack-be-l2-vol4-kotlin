package com.loopers.application.catalog

import com.loopers.application.catalog.port.CatalogProductQueryPort
import com.loopers.application.shopping.CartCatalogPort
import com.loopers.application.shopping.CartProductInfo
import org.springframework.stereotype.Component

@Component
class CartCatalogAdapter(
    private val catalogProductQueryPort: CatalogProductQueryPort,
) : CartCatalogPort {
    override fun getCartProduct(productId: Long): CartProductInfo? =
        catalogProductQueryPort.findDisplayableProductDetail(productId)?.product?.toCartProductInfo()

    override fun getCartProducts(productIds: Collection<Long>): List<CartProductInfo> =
        productIds.distinct().mapNotNull(::getCartProduct)

    private fun CatalogInfo.ProductDisplayRow.toCartProductInfo(): CartProductInfo =
        CartProductInfo(
            productId = productId,
            productName = productName,
            brandName = brandName,
            price = price,
            stockQuantity = stockQuantity,
            orderable = true,
        )
}
