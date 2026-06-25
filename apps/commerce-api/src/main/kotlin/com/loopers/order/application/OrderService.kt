package com.loopers.order.application

import com.loopers.order.domain.Order
import com.loopers.order.domain.OrderErrorCode
import com.loopers.order.domain.OrderItem
import com.loopers.order.domain.OrderItemSnapshot
import com.loopers.order.domain.OrderRepository
import com.loopers.order.domain.OrderStatus
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductErrorCode
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.NotFoundException
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OrderService(
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun create(command: OrderCreateCommand, products: Map<Long, Product>, discountAmount: Money): OrderInfo {
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

        val order = orderRepository.save(Order.create(command.userId, snapshots, command.couponId, discountAmount))
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
    val userId: Long,
    val items: List<OrderLineCommand>,
    val couponId: Long? = null,
    val expectedOriginalAmount: Long,
    val expectedDiscountAmount: Long,
) {
    init {
        if (items.isEmpty()) {
            throw BadRequestException(OrderErrorCode.EMPTY_ORDER_ITEMS)
        }
        val total = items.sumOf { it.price * it.quantity }
        if (total != expectedOriginalAmount) {
            throw BadRequestException(OrderErrorCode.ORDER_PRICE_NOT_MATCHED)
        }
        if (couponId == null && expectedDiscountAmount != 0L) {
            throw BadRequestException(OrderErrorCode.ORDER_PRICE_NOT_MATCHED)
        }
    }
}

data class OrderLineCommand(
    val productId: Long,
    val quantity: Int,
    val price: Long,
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
