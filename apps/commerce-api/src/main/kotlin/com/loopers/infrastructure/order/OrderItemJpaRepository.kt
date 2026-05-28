package com.loopers.infrastructure.order

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OrderItemJpaRepository : JpaRepository<OrderItemEntity, Long> {
    fun findAllByOrderId(orderId: Long): List<OrderItemEntity>

    fun findAllByOrderIdIn(orderIds: Collection<Long>): List<OrderItemEntity>

    @Query(
        """
        SELECT DISTINCT oi.orderId
        FROM OrderItemEntity oi
        WHERE oi.productId IN :productIds
        """,
    )
    fun findOrderIdsByProductIdIn(@Param("productIds") productIds: Collection<Long>): List<Long>
}
