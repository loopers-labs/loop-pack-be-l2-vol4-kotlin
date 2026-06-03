package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderItem
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "order_item",
    indexes = [
        Index(name = "idx_order_item_order_id", columnList = "order_id"),
        Index(name = "idx_order_item_product_id", columnList = "product_id"),
    ],
)
class OrderItemEntity(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,

    @Column(name = "snapshot_product_name", nullable = false, length = 255)
    val snapshotProductName: String,

    @Column(name = "snapshot_price", nullable = false)
    val snapshotPrice: Long,

    @Column(name = "snapshot_brand_name", nullable = false, length = 255)
    val snapshotBrandName: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    fun toDomain(): OrderItem = OrderItem(
        productId = productId,
        quantity = quantity,
        snapshotProductName = snapshotProductName,
        snapshotPrice = snapshotPrice,
        snapshotBrandName = snapshotBrandName,
    )

    companion object {
        fun from(orderId: Long, item: OrderItem): OrderItemEntity = OrderItemEntity(
            orderId = orderId,
            productId = item.productId,
            quantity = item.quantity,
            snapshotProductName = item.snapshotProductName,
            snapshotPrice = item.snapshotPrice,
            snapshotBrandName = item.snapshotBrandName,
        )
    }
}
