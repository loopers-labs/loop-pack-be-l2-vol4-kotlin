package com.loopers.domain.order

import com.loopers.domain.brand.Brand
import com.loopers.domain.inventory.Inventory
import com.loopers.domain.order.dto.OrderPlacementItem
import com.loopers.domain.order.dto.OrderPlacementResult
import com.loopers.domain.product.Product
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class OrderPlacementService {
    fun place(
        memberId: Long,
        items: List<OrderPlacementItem>,
        products: List<Product>,
        brands: List<Brand>,
        inventories: List<Inventory>,
    ): OrderPlacementResult {
        if (items.isEmpty()) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order items must not be empty.")
        }

        val productById = products.associateBy { it.id }
        val brandById = brands.associateBy { it.id }
        val inventoryByProductId = inventories.associateBy { it.productId }
        val normalizedItems = items
            .groupBy { it.productId }
            .map { (productId, duplicatedItems) ->
                OrderPlacementItem(
                    productId = productId,
                    quantity = duplicatedItems.sumOf { it.quantity },
                )
            }

        val orderItems = normalizedItems.map { item ->
            val product = productById[item.productId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "Product not found.")
            val brand = brandById[product.brandId]
                ?: throw CoreException(ErrorType.NOT_FOUND, "Brand not found.")
            val inventory = inventoryByProductId[product.id]
                ?: throw CoreException(ErrorType.NOT_FOUND, "Inventory not found.")

            inventory.deduct(item.quantity)
            OrderItem.snapshot(
                productId = product.id,
                productName = product.name,
                brandName = brand.name,
                unitPrice = product.price,
                quantity = item.quantity,
            )
        }

        return OrderPlacementResult(
            order = Order.createCompleted(memberId = memberId, items = orderItems),
            inventories = inventories,
        )
    }
}
