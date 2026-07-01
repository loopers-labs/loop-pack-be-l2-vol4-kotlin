package com.loopers.domain.order.infrastructure.persistence

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.model.OrderItemModel
import com.loopers.domain.order.model.OrderModel
import com.loopers.domain.order.model.OrderStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "orders")
class OrderJpaEntity(
    @Column(name = "ordered_user_id", nullable = false)
    var orderedUserId: Long,
    @Column(name = "idempotency_key", unique = true)
    var idempotencyKey: String? = null,
    @Column(name = "issued_coupon_id")
    var issuedCouponId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    var status: OrderStatus = OrderStatus.PAYMENT_PENDING,
    @Column(name = "total_price", nullable = false)
    var totalPrice: Long,
    @Column(name = "discount_price", nullable = false)
    var discountPrice: Long,
    @Column(name = "payment_price", nullable = false)
    var paymentPrice: Long,
) : BaseEntity() {
    fun toDomain(items: List<OrderItemModel>): OrderModel = OrderModel.fromPersisted(
        id = id,
        orderedUserId = orderedUserId,
        idempotencyKey = idempotencyKey,
        issuedCouponId = issuedCouponId,
        status = status,
        items = items,
        totalPrice = totalPrice,
        discountPrice = discountPrice,
        paymentPrice = paymentPrice,
    )

    companion object {
        fun fromDomain(order: OrderModel): OrderJpaEntity = OrderJpaEntity(
            orderedUserId = order.orderedUserId,
            idempotencyKey = order.idempotencyKey,
            issuedCouponId = order.issuedCouponId,
            status = order.status,
            totalPrice = order.totalPrice.value,
            discountPrice = order.discountPrice.value,
            paymentPrice = order.paymentPrice.value,
        )
    }
}
