package com.loopers.application.order

import com.loopers.application.brand.BrandService
import com.loopers.application.inventory.InventoryService
import com.loopers.application.order.dto.OrderCreateCommand
import com.loopers.application.order.dto.OrderInfo
import com.loopers.application.product.ProductService
import com.loopers.application.user.UserService
import com.loopers.domain.order.OrderPlacementService
import com.loopers.domain.order.dto.OrderPlacementItem
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val userService: UserService,
    private val productService: ProductService,
    private val brandService: BrandService,
    private val inventoryService: InventoryService,
    private val orderPlacementService: OrderPlacementService,
) {
    @Transactional
    fun placeOrder(
        loginId: String,
        rawPassword: String,
        command: OrderCreateCommand,
    ): OrderInfo {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)
        val requestedItems = command.items.map { item ->
            OrderPlacementItem(productId = item.productId, quantity = item.quantity)
        }
        val productIds = requestedItems.map { it.productId }
        val products = productService.getProducts(productIds)
        val brands = brandService.getBrands(products.map { it.brandId })
        val inventories = inventoryService.getInventoriesForUpdate(productIds)
        val result = orderPlacementService.place(
            memberId = user.id,
            items = requestedItems,
            products = products,
            brands = brands,
            inventories = inventories,
        )

        inventoryService.updateInventories(result.inventories)
        return orderService.save(result.order)
            .let(OrderInfo::from)
    }
}
