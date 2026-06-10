package com.loopers.infrastructure.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderAmount
import com.loopers.domain.order.OrderAmounts
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
    userCouponId: Long?,
    status: OrderStatus,
    totalAmount: Long,
    discountAmount: Long,
    paymentAmount: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Column(name = "user_coupon_id")
    val userCouponId: Long? = userCouponId

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: OrderStatus = status
        protected set

    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long = totalAmount
        protected set

    @Column(name = "discount_amount", nullable = false)
    var discountAmount: Long = discountAmount
        protected set

    @Column(name = "payment_amount", nullable = false)
    var paymentAmount: Long = paymentAmount
        protected set

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val _items: MutableList<OrderItemJpaEntity> = mutableListOf()

    val items: List<OrderItemJpaEntity>
        get() = _items.toList()

    fun updateFrom(order: Order) {
        status = order.status
        totalAmount = order.totalAmount.amount
        discountAmount = order.discountAmount.amount
        paymentAmount = order.paymentAmount.amount
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
            userCouponId = userCouponId,
            items = items.map { it.toDomain() },
            status = status,
            amounts = OrderAmounts(
                totalAmount = OrderAmount(totalAmount),
                discountAmount = OrderAmount(discountAmount),
                paymentAmount = OrderAmount(paymentAmount),
            ),
        )
    }

    companion object {
        fun from(order: Order): OrderJpaEntity {
            val orderEntity = OrderJpaEntity(
                userId = order.userId,
                userCouponId = order.userCouponId,
                status = order.status,
                totalAmount = order.totalAmount.amount,
                discountAmount = order.discountAmount.amount,
                paymentAmount = order.paymentAmount.amount,
            )
            order.items.forEach { orderEntity.addItem(it) }
            return orderEntity
        }
    }
}
