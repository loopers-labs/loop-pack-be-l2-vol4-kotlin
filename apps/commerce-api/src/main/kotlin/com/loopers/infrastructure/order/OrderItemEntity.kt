package com.loopers.infrastructure.order

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "order_item")
class OrderItemEntity(
    @Column(name = "product_id", nullable = false)
    var productId: Long,

    @Column(name = "product_name_snapshot", nullable = false)
    var productName: String,

    @Column(name = "brand_name_snapshot", nullable = false)
    var brandName: String,

    @Column(name = "unit_price_amount_snapshot", nullable = false)
    var unitPrice: Long,

    @Column(nullable = false)
    var quantity: Long,

    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long,
) : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    lateinit var order: OrderEntity
}
