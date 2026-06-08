package com.loopers.infrastructure.order

import com.loopers.domain.order.AppliedCoupon
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderItems
import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.ZonedDateTime

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "orders",
    indexes = [
        Index(name = "idx_orders_user_id", columnList = "user_id"),
        Index(name = "idx_orders_ordered_at", columnList = "ordered_at"),
    ],
)
class OrderEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: OrderStatusType,

    @Column(name = "total_amount", nullable = false)
    val totalAmount: Long,

    @Column(name = "used_point", nullable = false)
    val usedPoint: Long,

    @Column(name = "ordered_at", nullable = false)
    val orderedAt: ZonedDateTime,

    @Convert(converter = AppliedCouponConverter::class)
    @Column(name = "coupon_snapshot", columnDefinition = "TEXT")
    val appliedCoupon: AppliedCoupon?,
) : BaseEntity() {

    fun updateStatus(next: OrderStatusType) {
        this.status = next
    }

    fun toDomain(items: List<OrderItemEntity>): Order = Order(
        id = id,
        userId = userId,
        status = status.toDomain(),
        totalAmount = totalAmount,
        usedPoint = usedPoint,
        orderedAt = orderedAt,
        items = OrderItems(items.map { it.toDomain() }),
        appliedCoupon = appliedCoupon,
    )

    companion object {
        fun from(domain: Order): OrderEntity = OrderEntity(
            userId = domain.userId,
            status = OrderStatusType.from(domain.status),
            totalAmount = domain.totalAmount,
            usedPoint = domain.usedPoint,
            orderedAt = domain.orderedAt,
            appliedCoupon = domain.appliedCoupon,
        )
    }
}
