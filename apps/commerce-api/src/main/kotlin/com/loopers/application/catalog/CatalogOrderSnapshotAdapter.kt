package com.loopers.application.catalog

import com.loopers.application.catalog.port.CatalogProductQueryPort
import com.loopers.application.order.OrderCatalogPort
import com.loopers.application.order.OrderCatalogProductInfo
import org.springframework.stereotype.Component

@Component
class CatalogOrderSnapshotAdapter(
    private val catalogProductQueryPort: CatalogProductQueryPort,
) : OrderCatalogPort {
    override fun getOrderProducts(productIds: Collection<Long>): List<OrderCatalogProductInfo> =
        productIds.distinct()
            .mapNotNull { productId -> catalogProductQueryPort.findDisplayableProductDetail(productId)?.product }
            .map { product ->
                OrderCatalogProductInfo(
                    productId = product.productId,
                    productName = product.productName,
                    brandName = product.brandName,
                    price = product.price,
                    orderable = product.availableQuantity > 0,
                )
            }
}
