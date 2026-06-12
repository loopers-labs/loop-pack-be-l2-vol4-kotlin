package com.loopers.domain.order.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

@Embeddable
data class OrderItemJpaId(
    @Column(name = "order_id", nullable = false)
    var orderId: Long = 0L,
    @Column(name = "product_id", nullable = false)
    var productId: Long = 0L,
) : Serializable
