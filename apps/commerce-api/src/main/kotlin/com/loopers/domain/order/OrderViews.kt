package com.loopers.domain.order

import java.time.ZonedDateTime

data class OrderItemView(
    val productId: Long,
    val quantity: Int,
    val snapshotProductName: String,
    val snapshotPrice: Long,
    val snapshotBrandName: String,
    val subtotal: Long,
) {
    companion object {
        fun from(item: OrderItem): OrderItemView = OrderItemView(
            productId = item.productId,
            quantity = item.quantity,
            snapshotProductName = item.snapshotProductName,
            snapshotPrice = item.snapshotPrice,
            snapshotBrandName = item.snapshotBrandName,
            subtotal = item.subtotal(),
        )
    }
}

data class OrderDetail(
    val id: Long,
    val status: OrderStatus,
    val totalAmount: Long,
    val discountAmount: Long,
    val usedPoint: Long,
    val actualAmount: Long,
    val earnPoint: Long,
    val orderedAt: ZonedDateTime,
    val items: List<OrderItemView>,
    val appliedCoupon: AppliedCoupon?,
) {
    companion object {
        fun from(order: Order): OrderDetail = OrderDetail(
            id = order.id,
            status = order.status,
            totalAmount = order.totalAmount,
            discountAmount = order.discountAmount,
            usedPoint = order.usedPoint,
            actualAmount = order.getActualAmount(),
            earnPoint = order.getEarnPoint(),
            orderedAt = order.orderedAt,
            items = order.items.list.map { OrderItemView.from(it) },
            appliedCoupon = order.appliedCoupon,
        )
    }
}

data class OrderSummary(
    val id: Long,
    val status: OrderStatus,
    val totalAmount: Long,
    val usedPoint: Long,
    val orderedAt: ZonedDateTime,
) {
    companion object {
        fun from(order: Order): OrderSummary = OrderSummary(
            id = order.id,
            status = order.status,
            totalAmount = order.totalAmount,
            usedPoint = order.usedPoint,
            orderedAt = order.orderedAt,
        )
    }
}

data class AdminOrderSummary(
    val id: Long,
    val userId: Long,
    val loginId: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val orderedAt: ZonedDateTime,
) {
    companion object {
        fun of(order: Order, loginId: String): AdminOrderSummary = AdminOrderSummary(
            id = order.id,
            userId = order.userId,
            loginId = loginId,
            status = order.status,
            totalAmount = order.totalAmount,
            orderedAt = order.orderedAt,
        )
    }
}

data class AdminOrderDetail(
    val id: Long,
    val userId: Long,
    val loginId: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val discountAmount: Long,
    val usedPoint: Long,
    val actualAmount: Long,
    val earnPoint: Long,
    val orderedAt: ZonedDateTime,
    val items: List<OrderItemView>,
    val appliedCoupon: AppliedCoupon?,
) {
    companion object {
        fun of(order: Order, loginId: String): AdminOrderDetail = AdminOrderDetail(
            id = order.id,
            userId = order.userId,
            loginId = loginId,
            status = order.status,
            totalAmount = order.totalAmount,
            discountAmount = order.discountAmount,
            usedPoint = order.usedPoint,
            actualAmount = order.getActualAmount(),
            earnPoint = order.getEarnPoint(),
            orderedAt = order.orderedAt,
            items = order.items.list.map { OrderItemView.from(it) },
            appliedCoupon = order.appliedCoupon,
        )
    }
}
