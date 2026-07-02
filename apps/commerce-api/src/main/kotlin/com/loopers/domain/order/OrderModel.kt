package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "orders")
class OrderModel(
    userId: Long,
    totalPrice: Long = 0,
    originalPrice: Long = 0,
    discountAmount: Long = 0,
    couponId: Long? = null,
) : BaseEntity() {

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "total_price", nullable = false)
    var totalPrice: Long = totalPrice
        protected set

    @Column(name = "original_price", nullable = false)
    var originalPrice: Long = originalPrice
        protected set

    @Column(name = "discount_amount", nullable = false)
    var discountAmount: Long = discountAmount
        protected set

    @Column(name = "coupon_id")
    var couponId: Long? = couponId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus = OrderStatus.PENDING
        protected set

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val orderItems: MutableList<OrderItemModel> = mutableListOf()

    fun addItem(item: OrderItemModel) {
        orderItems.add(item)
        item.order = this
    }

    fun calculateTotalPrice(): Long = orderItems.sumOf { it.itemTotalPrice() }

    fun applyCoupon(couponId: Long, discountAmount: Long) {
        this.couponId = couponId
        this.discountAmount = discountAmount
        this.totalPrice = maxOf(originalPrice - discountAmount, 0)
    }

    fun markPaid() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "결제 완료 처리할 수 없는 주문 상태입니다. (status=$status)",
            )
        }
        this.status = OrderStatus.PAID
    }

    fun markCancelled() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "취소 처리할 수 없는 주문 상태입니다. (status=$status)",
            )
        }
        this.status = OrderStatus.CANCELLED
    }

    companion object {
        fun create(
            userId: Long,
            items: List<OrderItemModel>,
            discountAmount: Long = 0,
            couponId: Long? = null,
        ): OrderModel {
            if (items.isEmpty()) {
                throw CoreException(
                    errorType = ErrorType.BAD_REQUEST,
                    customMessage = "주문 항목이 비어있습니다.",
                )
            }
            val order = OrderModel(userId = userId)
            items.forEach { order.addItem(it) }
            val originalPrice = order.calculateTotalPrice()
            order.originalPrice = originalPrice
            order.discountAmount = discountAmount
            order.totalPrice = maxOf(originalPrice - discountAmount, 0)
            order.couponId = couponId
            return order
        }
    }
}
