package com.loopers.infrastructure.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_orders_user_id", columnList = "user_id"),
    ],
)
class OrderJpaEntity(
    userId: Long,
    status: OrderStatus,
    totalPrice: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: OrderStatus = status
        protected set

    @Column(name = "total_amount", nullable = false)
    var totalPrice: Long = totalPrice
        protected set

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val _items: MutableList<OrderItemJpaEntity> = mutableListOf()

    val items: List<OrderItemJpaEntity>
        get() = _items.toList()

    fun updateFrom(order: Order) {
        status = order.status
        totalPrice = order.totalPrice.amount
    }

    fun addItem(orderItem: OrderItem) {
        _items.add(
            OrderItemJpaEntity(
                productId = orderItem.productId,
                productName = orderItem.productName,
                productPrice = orderItem.productPrice.amount,
                quantity = orderItem.quantity.value,
                totalPrice = orderItem.totalPrice.amount,
                order = this,
            ),
        )
    }

    fun toDomain(): Order {
        return Order(
            id = id,
            userId = userId,
            items = items.map { it.toDomain() },
            status = status,
        )
    }

    companion object {
        fun from(order: Order): OrderJpaEntity {
            val orderEntity = OrderJpaEntity(
                userId = order.userId,
                status = order.status,
                totalPrice = order.totalPrice.amount,
            )
            order.items.forEach { orderEntity.addItem(it) }
            return orderEntity
        }
    }
}
