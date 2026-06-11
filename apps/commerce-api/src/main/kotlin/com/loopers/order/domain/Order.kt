package com.loopers.order.domain

import com.loopers.domain.BaseEntity
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import jakarta.persistence.AttributeOverride
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.Collections

@Entity
@Table(name = "orders")
class Order private constructor(
    userId: Long,
    orderedAt: LocalDateTime,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long = userId

    @Column(name = "ordered_at", nullable = false, updatable = false)
    val orderedAt: LocalDateTime = orderedAt

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false))
    var totalAmount: Money = Money(0)
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.CREATED
        private set

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val orderItems: MutableList<OrderItem> = mutableListOf()

    val items: List<OrderItem>
        get() = Collections.unmodifiableList(orderItems)

    private fun addItem(snapshot: OrderItemSnapshot) {
        orderItems.add(
            OrderItem(
                order = this,
                productId = snapshot.productId,
                brandId = snapshot.brandId,
                productName = snapshot.productName,
                brandName = snapshot.brandName,
                unitPrice = snapshot.unitPrice,
                quantity = snapshot.quantity,
            ),
        )
        totalAmount = Money(orderItems.sumOf { it.subtotal().amount })
    }

    companion object {
        fun create(userId: Long, snapshots: List<OrderItemSnapshot>): Order {
            if (snapshots.isEmpty()) {
                throw BadRequestException(OrderErrorCode.EMPTY_ORDER_ITEMS)
            }
            val order = Order(userId, LocalDateTime.now())
            snapshots.forEach { order.addItem(it) }
            return order
        }
    }
}

data class OrderItemSnapshot(
    val productId: Long,
    val brandId: Long?,
    val productName: String,
    val brandName: String?,
    val unitPrice: Money,
    val quantity: Int,
)
