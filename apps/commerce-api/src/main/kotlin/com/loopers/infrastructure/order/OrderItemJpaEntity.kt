package com.loopers.infrastructure.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderItemPrice
import com.loopers.domain.order.OrderQuantity
import com.loopers.domain.order.ProductSnapshot
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(
    name = "order_items",
    indexes = [
        Index(name = "idx_order_items_order_id", columnList = "order_id"),
        Index(name = "idx_order_items_product_id", columnList = "product_id"),
    ],
)
class OrderItemJpaEntity(
    productId: Long,
    productName: String,
    productPrice: Long,
    quantity: Int,
    totalPrice: Long,
    order: OrderJpaEntity,
) : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        foreignKey = ForeignKey(ConstraintMode.NO_CONSTRAINT),
    )
    private val order: OrderJpaEntity = order

    @Column(name = "product_id", nullable = false)
    val productId: Long = productId

    @Column(name = "product_name", nullable = false, length = 100)
    val productName: String = productName

    @Column(name = "product_price", nullable = false)
    val productPrice: Long = productPrice

    @Column(name = "quantity", nullable = false)
    val quantity: Int = quantity

    @Column(name = "total_price", nullable = false)
    val totalPrice: Long = totalPrice

    fun toDomain(): OrderItem {
        return OrderItem(
            id = id,
            productSnapshot = ProductSnapshot(
                productId = productId,
                productName = productName,
                productPrice = OrderItemPrice(productPrice),
            ),
            quantity = OrderQuantity(quantity),
        )
    }
}
