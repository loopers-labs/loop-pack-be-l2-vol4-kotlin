package com.loopers.domain.order.infrastructure.persistence

import com.loopers.domain.order.model.OrderItemModel
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.Quantity
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable
import java.time.ZonedDateTime

@Entity
@Table(name = "order_items")
class OrderItemJpaEntity(
    @EmbeddedId
    private var orderItemId: OrderItemJpaId,
    @Column(nullable = false)
    var quantity: Long,
    @Column(name = "snapshot_product_name", nullable = false)
    var snapshotProductName: String,
    @Column(name = "snapshot_unit_price", nullable = false)
    var snapshotUnitPrice: Long,
    @Column(name = "line_price", nullable = false)
    var linePrice: Long,
) : Persistable<OrderItemJpaId> {
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @PrePersist
    private fun prePersist() {
        createdAt = ZonedDateTime.now()
    }

    @Transient
    override fun getId(): OrderItemJpaId = orderItemId

    @Transient
    override fun isNew(): Boolean = !this::createdAt.isInitialized

    fun toDomain(): OrderItemModel = OrderItemModel.snapshotOf(
        orderId = orderItemId.orderId,
        productId = orderItemId.productId,
        quantity = Quantity.of(quantity),
        snapshotProductName = snapshotProductName,
        snapshotUnitPrice = Money.of(snapshotUnitPrice),
    )

    companion object {
        fun fromDomain(item: OrderItemModel): OrderItemJpaEntity = OrderItemJpaEntity(
            orderItemId = OrderItemJpaId(
                orderId = item.orderId,
                productId = item.productId,
            ),
            quantity = item.quantity.value,
            snapshotProductName = item.snapshotProductName,
            snapshotUnitPrice = item.snapshotUnitPrice.value,
            linePrice = item.linePrice.value,
        )
    }
}
