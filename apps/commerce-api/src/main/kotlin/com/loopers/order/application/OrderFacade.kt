package com.loopers.order.application

import com.loopers.coupon.application.CouponService
import com.loopers.inventory.application.InventoryService
import com.loopers.inventory.application.StockDecreaseLine
import com.loopers.order.domain.OrderErrorCode
import com.loopers.product.application.ProductService
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class OrderFacade(
    private val productService: ProductService,
    private val couponService: CouponService,
    private val inventoryService: InventoryService,
    private val orderService: OrderService,
) {
    // PG 연동(O-F1) 시 이 메서드의 Tx 구간을 안쪽 메서드로 분리한다 — 외부 호출은 Tx 밖에 둔다
    @Transactional
    fun place(userId: Long, command: OrderCreateCommand): OrderInfo {
        if (command.items.isEmpty()) {
            throw BadRequestException(OrderErrorCode.EMPTY_ORDER_ITEMS)
        }
        val products = productService.getActiveProducts(command.items.map { it.productId })
        val originalAmount = Money(command.items.sumOf { products.getValue(it.productId).price.amount * it.quantity })

        val discountAmount = command.couponId
            ?.let { couponService.use(userId, it, originalAmount, LocalDateTime.now()) }
            ?: Money(0)

        val info = orderService.create(userId, command, products, discountAmount)
        // 비관락(재고)은 맨 마지막 — 행 점유 시간 최소화
        inventoryService.decreaseStock(command.items.map { StockDecreaseLine(it.productId, it.quantity.toLong()) })
        return info
    }
}
