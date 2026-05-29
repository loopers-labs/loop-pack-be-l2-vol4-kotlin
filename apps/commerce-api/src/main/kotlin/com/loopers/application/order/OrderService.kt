package com.loopers.application.order

import com.loopers.domain.inventory.InventoryErrorCode
import com.loopers.domain.inventory.InventoryRepository
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorCode
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderItemSnapshot
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.ProductErrorCode
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository,
) {
    @Transactional
    fun place(userId: Long, command: OrderCreateCommand): OrderInfo {
        if (command.items.isEmpty()) {
            throw BadRequestException(OrderErrorCode.EMPTY_ORDER_ITEMS)
        }
        val productIds = command.items.map { it.productId }
        val products = productRepository.findAllActiveByIdIn(productIds).associateBy { it.id }
        val inventories = inventoryRepository.findAllByProductIdInForUpdate(productIds).associateBy { it.productId }

        val snapshots = command.items.map { line ->
            val product = products[line.productId]
                ?: throw NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND)
            val inventory = inventories[line.productId]
                ?: throw NotFoundException(InventoryErrorCode.INVENTORY_NOT_FOUND)
            inventory.decrease(line.quantity.toLong())
            OrderItemSnapshot(
                productId = product.id,
                brandId = product.brandId,
                productName = product.name.value,
                brandName = null,
                unitPrice = product.price,
                quantity = line.quantity,
            )
        }
        val order = orderRepository.save(Order.create(userId, snapshots))
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
)

data class OrderLineCommand(
    val productId: Long,
    val quantity: Int,
)

data class OrderInfo(
    val id: Long,
    val userId: Long,
    val orderedAt: LocalDateTime,
    val totalAmount: Long,
    val status: OrderStatus,
    val items: List<OrderItemInfo>,
) {
    companion object {
        fun from(order: Order): OrderInfo =
            OrderInfo(
                id = order.id,
                userId = order.userId,
                orderedAt = order.orderedAt,
                totalAmount = order.totalAmount.amount,
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
