package com.loopers.infrastructure.order.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.OrderStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.BatchSize
import java.time.ZonedDateTime

@Entity
@Table(
    name = "orders",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_orders_order_number", columnNames = ["order_number"]),
    ],
)
class OrderEntity(
    @Column(name = "order_number", nullable = false)
    var orderNumber: String,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: OrderStatus,

    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long,

    @Column(name = "original_amount", nullable = false)
    var originalAmount: Long = totalAmount,

    @Column(name = "discount_amount", nullable = false)
    var discountAmount: Long = 0L,

    @Column(name = "coupon_issue_id")
    var couponIssueId: Long? = null,

    @Column(name = "ordered_at", nullable = false)
    var orderedAt: ZonedDateTime,
) : BaseEntity() {
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    @BatchSize(size = 50)
    val items: MutableList<OrderItemEntity> = mutableListOf()

    fun addItem(item: OrderItemEntity) {
        items.add(item)
        item.order = this
    }

    fun updateStatus(status: OrderStatus) {
        this.status = status
    }
}
