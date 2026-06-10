package com.loopers.application.order

import com.loopers.application.brand.BrandService
import com.loopers.application.coupon.CouponService
import com.loopers.application.inventory.InventoryService
import com.loopers.application.order.dto.OrderCreateCommand
import com.loopers.application.order.dto.OrderInfo
import com.loopers.application.order.dto.OrderSummaryInfo
import com.loopers.application.product.ProductService
import com.loopers.application.user.UserService
import com.loopers.domain.order.OrderPlacementService
import com.loopers.domain.order.dto.OrderPlacementItem
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class OrderFacade(
    private val orderService: OrderService,
    private val userService: UserService,
    private val productService: ProductService,
    private val brandService: BrandService,
    private val inventoryService: InventoryService,
    private val couponService: CouponService,
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
        val couponIssue = command.couponId?.let(couponService::getCouponIssue)
        val result = orderPlacementService.place(
            memberId = user.id,
            items = requestedItems,
            products = products,
            brands = brands,
            inventories = inventories,
            couponIssue = couponIssue,
        )

        inventoryService.updateInventories(result.inventories)
        result.couponIssue?.let(couponService::saveCouponIssue)
        return orderService.save(result.order)
            .let(OrderInfo::from)
    }

    @Transactional(readOnly = true)
    fun getOrders(
        loginId: String,
        rawPassword: String,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
    ): List<OrderSummaryInfo> {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)

        return orderService.getOrders(
            memberId = user.id,
            startAt = startAt,
            endAt = endAt,
        )
    }

    @Transactional(readOnly = true)
    fun getOrder(
        loginId: String,
        rawPassword: String,
        orderId: Long,
    ): OrderInfo {
        val user = userService.getMe(loginId = loginId, rawPassword = rawPassword)

        return orderService.getOrder(memberId = user.id, orderId = orderId)
    }

    @Transactional(readOnly = true)
    fun getOrdersForAdmin(page: Int, size: Int): Page<OrderSummaryInfo> {
        return orderService.getOrders(page = page, size = size)
    }

    @Transactional(readOnly = true)
    fun getOrderForAdmin(orderId: Long): OrderInfo {
        return orderService.getOrder(orderId)
    }
}
