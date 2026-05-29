package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem

object OrderMapper {
    fun toDomain(order: OrderEntity): Order {
        return Order(
            id = order.id,
            orderNumber = order.orderNumber,
            memberId = order.memberId,
            status = order.status,
            totalAmount = order.totalAmount,
            orderedAt = order.orderedAt,
            items = order.items.map { item ->
                OrderItem(
                    id = item.id,
                    productId = item.productId,
                    productName = item.productName,
                    brandName = item.brandName,
                    unitPrice = item.unitPrice,
                    quantity = item.quantity,
                    totalAmount = item.totalAmount,
                )
            },
        )
    }

    fun toEntity(order: Order): OrderEntity {
        val entity = OrderEntity(
            orderNumber = order.orderNumber,
            memberId = order.memberId,
            status = order.status,
            totalAmount = order.totalAmount,
            orderedAt = order.orderedAt,
        )
        order.items
            .map { item ->
                OrderItemEntity(
                    productId = item.productId,
                    productName = item.productName,
                    brandName = item.brandName,
                    unitPrice = item.unitPrice,
                    quantity = item.quantity,
                    totalAmount = item.totalAmount,
                )
            }
            .forEach(entity::addItem)

        return entity
    }
}
