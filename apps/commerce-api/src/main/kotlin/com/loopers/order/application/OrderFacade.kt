package com.loopers.order.application

import com.loopers.coupon.application.CouponService
import com.loopers.inventory.application.InventoryService
import com.loopers.inventory.application.StockDecreaseLine
import com.loopers.inventory.application.StockRestoreLine
import com.loopers.order.domain.Order
import com.loopers.product.application.ProductCheckCommand
import com.loopers.product.application.ProductService
import com.loopers.shared.domain.Money
import java.time.LocalDateTime
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderFacade(
    private val productService: ProductService,
    private val couponService: CouponService,
    private val inventoryService: InventoryService,
    private val orderService: OrderService,
) {
    // PG 연동(O-F1) 시 이 메서드의 Tx 구간을 안쪽 메서드로 분리한다 — 외부 호출은 Tx 밖에 둔다
    @Transactional
    fun place(command: OrderCreateCommand): OrderInfo {
        val products = productService.getActiveProducts(command.items.map { ProductCheckCommand(it.productId, it.price) })
        val discountAmount = command.couponId
            ?.let {
                couponService.use(
                    command.userId,
                    it,
                    command.expectedOriginalAmount,
                    command.expectedDiscountAmount,
                    LocalDateTime.now(),
                )
            }
            ?: Money(0)

        val info = orderService.create(command, products, discountAmount)
        inventoryService.decreaseStock(command.items.map { StockDecreaseLine(it.productId, it.quantity.toLong()) })
        return info
    }

    @Transactional
    fun cancelAndCompensate(order: Order) {
        order.failPayment()
        inventoryService.increaseStock(order.items.map { StockRestoreLine(it.productId, it.quantity.toLong()) })
        order.couponId?.let { couponService.cancelUse(order.userId, it) }
    }
}
