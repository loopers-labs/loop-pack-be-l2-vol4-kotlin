package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderCancelReason
import com.loopers.domain.order.OrderStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OrderJpaRepository : JpaRepository<Order, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update OrderEntity orderEntity
           set orderEntity.status = :nextStatus
         where orderEntity.id = :orderId
           and orderEntity.status = :currentStatus
           and orderEntity.deletedAt is null
        """,
    )
    fun updateStatus(
        @Param("orderId") orderId: Long,
        @Param("currentStatus") currentStatus: OrderStatus,
        @Param("nextStatus") nextStatus: OrderStatus,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update OrderEntity orderEntity
           set orderEntity.status = :nextStatus,
               orderEntity.cancelReason = :cancelReason
         where orderEntity.id = :orderId
           and orderEntity.status = :currentStatus
           and orderEntity.deletedAt is null
        """,
    )
    fun cancelByCurrentStatus(
        @Param("orderId") orderId: Long,
        @Param("cancelReason") cancelReason: OrderCancelReason,
        @Param("currentStatus") currentStatus: OrderStatus,
        @Param("nextStatus") nextStatus: OrderStatus,
    ): Int

    fun findAllByStatusAndReservationExpiresAtBefore(status: OrderStatus, now: LocalDateTime): List<Order>
}
