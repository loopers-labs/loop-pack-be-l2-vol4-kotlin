package com.loopers.order.application

import com.loopers.coupon.application.CouponService
import com.loopers.inventory.domain.InventoryErrorCode
import com.loopers.inventory.domain.InventoryRepository
import com.loopers.order.domain.Order
import com.loopers.order.domain.OrderErrorCode
import com.loopers.order.domain.OrderItem
import com.loopers.order.domain.OrderItemSnapshot
import com.loopers.order.domain.OrderRepository
import com.loopers.order.domain.OrderStatus
import com.loopers.product.domain.ProductErrorCode
import com.loopers.product.domain.ProductRepository
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
    private val couponService: CouponService,
) {
    @Transactional
    fun place(userId: Long, command: OrderCreateCommand): OrderInfo {
        if (command.items.isEmpty()) {
            throw BadRequestException(OrderErrorCode.EMPTY_ORDER_ITEMS)
        }
        val productIds = command.items.map { it.productId }
        val products = productRepository.findAllActiveByIdIn(productIds).associateBy { it.id }

        val snapshots = command.items.map { line ->
            val product = products[line.productId]
                ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
            OrderItemSnapshot(
                productId = product.id,
                brandId = product.brandId,
                productName = product.name.value,
                brandName = null,
                unitPrice = product.price,
                quantity = line.quantity,
            )
        }
        val originalAmount = Money(snapshots.sumOf { it.unitPrice.amount * it.quantity })

        val discountAmount = command.couponId
            ?.let { couponService.use(userId, it, originalAmount, LocalDateTime.now()) }
            ?: Money(0)
        val totalAmount = Money(originalAmount.amount - discountAmount.amount)

        if (command.expectedOriginalAmount != originalAmount.amount ||
            command.expectedDiscountAmount != discountAmount.amount ||
            command.expectedTotalAmount != totalAmount.amount
        ) {
            throw ConflictException(OrderErrorCode.PRICE_CHANGED)
        }

        // FOR UPDATE 는 ID 정렬 순서로 — 교차 주문 데드락 차단
        val inventories = inventoryRepository.findAllByProductIdInForUpdate(productIds.distinct().sorted())
            .associateBy { it.productId }
        command.items.forEach { line ->
            val inventory = inventories[line.productId]
                ?: throw NotFoundException(InventoryErrorCode.INVENTORY_NOT_FOUND)
            inventory.decrease(line.quantity.toLong())
        }

        val order = orderRepository.save(Order.create(userId, snapshots, command.couponId, discountAmount))
        return OrderInfo.from(order)
    }

    @Transactional(readOnly = true)
    fun findById(orderId: Long, requesterUserId: Long): OrderInfo {
        val order = orderRepository.findById(orderId)
            ?: throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)
        if (order.userId != requesterUserId) {
            throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)
        }
        return OrderInfo.from(order)
    }

    @Transactional(readOnly = true)
    fun findMine(userId: Long, startAt: LocalDateTime, endAt: LocalDateTime): List<OrderInfo> =
        orderRepository.findByUserIdAndOrderedAtBetween(userId, startAt, endAt).map(OrderInfo::from)
}

data class OrderCreateCommand(
    val items: List<OrderLineCommand>,
    val couponId: Long? = null,
    val expectedOriginalAmount: Long,
    val expectedDiscountAmount: Long,
    val expectedTotalAmount: Long,
)

data class OrderLineCommand(
    val productId: Long,
    val quantity: Int,
)

data class OrderInfo(
    val id: Long,
    val userId: Long,
    val orderedAt: LocalDateTime,
    val originalAmount: Long,
    val discountAmount: Long,
    val totalAmount: Long,
    val couponId: Long?,
    val status: OrderStatus,
    val items: List<OrderItemInfo>,
) {
    companion object {
        fun from(order: Order): OrderInfo =
            OrderInfo(
                id = order.id,
                userId = order.userId,
                orderedAt = order.orderedAt,
                originalAmount = order.originalAmount.amount,
                discountAmount = order.discountAmount.amount,
                totalAmount = order.totalAmount.amount,
                couponId = order.couponId,
                status = order.status,
                items = order.items.map(OrderItemInfo::from),
            )
    }
}

data class OrderItemInfo(
    val productId: Long,
    val brandId: Long?,
    val productName: String,
    val brandName: String?,
    val unitPrice: Long,
    val quantity: Int,
) {
    companion object {
        fun from(item: OrderItem): OrderItemInfo =
            OrderItemInfo(
                productId = item.productId,
                brandId = item.brandId,
                productName = item.productName,
                brandName = item.brandName,
                unitPrice = item.unitPrice.amount,
                quantity = item.quantity,
            )
    }
}
