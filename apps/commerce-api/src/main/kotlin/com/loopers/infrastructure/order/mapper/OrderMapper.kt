package com.loopers.infrastructure.order.mapper

import com.loopers.domain.order.model.Order
import com.loopers.domain.order.model.OrderItem
import com.loopers.infrastructure.order.entity.OrderEntity
import com.loopers.infrastructure.order.entity.OrderItemEntity

object OrderMapper {
    fun toDomain(order: OrderEntity): Order {
        return Order(
            id = order.id,
            orderNumber = order.orderNumber,
            memberId = order.memberId,
            status = order.status,
            originalAmount = order.originalAmount,
            discountAmount = order.discountAmount,
            totalAmount = order.totalAmount,
            couponIssueId = order.couponIssueId,
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
            originalAmount = order.originalAmount,
            discountAmount = order.discountAmount,
            couponIssueId = order.couponIssueId,
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
