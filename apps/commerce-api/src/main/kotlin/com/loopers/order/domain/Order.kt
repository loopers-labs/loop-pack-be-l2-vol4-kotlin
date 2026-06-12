package com.loopers.order.domain

import com.loopers.domain.BaseEntity
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
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
    couponId: Long?,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long = userId

    @Column(name = "ordered_at", nullable = false, updatable = false)
    val orderedAt: LocalDateTime = orderedAt

    @Column(name = "coupon_id", updatable = false)
    val couponId: Long? = couponId

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "original_amount", nullable = false))
    var originalAmount: Money = Money(0)
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "discount_amount", nullable = false))
    var discountAmount: Money = Money(0)
        private set

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "total_amount", nullable = false))
    var totalAmount: Money = Money(0)
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING_PAYMENT
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
        originalAmount = Money(orderItems.sumOf { it.subtotal().amount })
    }

    private fun applyDiscount(discount: Money) {
        discountAmount = discount
        totalAmount = Money(originalAmount.amount - discount.amount)
    }

    fun confirmPayment() = transitionTo(OrderStatus.PAID)

    fun failPayment() = transitionTo(OrderStatus.FAILED)

    fun markUnknown() = transitionTo(OrderStatus.UNKNOWN)

    private fun transitionTo(target: OrderStatus) {
        if (!status.canTransitionTo(target)) {
            throw ConflictException(OrderErrorCode.INVALID_STATUS_TRANSITION)
        }
        status = target
    }

    companion object {
        fun create(
            userId: Long,
            snapshots: List<OrderItemSnapshot>,
            couponId: Long? = null,
            discountAmount: Money = Money(0),
        ): Order {
            if (snapshots.isEmpty()) {
                throw BadRequestException(OrderErrorCode.EMPTY_ORDER_ITEMS)
            }
            val order = Order(userId, LocalDateTime.now(), couponId)
            snapshots.forEach { order.addItem(it) }
            order.applyDiscount(discountAmount)
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
